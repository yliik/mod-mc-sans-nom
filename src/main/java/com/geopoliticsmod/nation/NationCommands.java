package com.geopoliticsmod.nation;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.geopoliticsmod.core.ActionResult;
import com.geopoliticsmod.core.Lang;
import com.geopoliticsmod.territory.TerritoryManager;
import com.geopoliticsmod.territory.TerritorySavedData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Commande {@code /nation} : accès joueur au module nations (en attendant les écrans de la phase 5). */
public final class NationCommands {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private static final SuggestionProvider<CommandSourceStack> NATIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(NationSavedData.get(ctx.getSource().getServer()).all().stream().map(Nation::name), builder);

    private static final SuggestionProvider<CommandSourceStack> INVITES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        List<String> names = player == null ? List.of() : NationManager.pendingInvites(ctx.getSource().getServer(), player.getUUID());
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        return SharedSuggestionProvider.suggest(ownNation(ctx.getSource()).stream()
                .flatMap(n -> n.members().keySet().stream())
                .map(id -> NationManager.playerName(server, id)), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> CITIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ownNation(ctx.getSource()).stream()
                    .flatMap(n -> n.cities().stream()).map(City::name), builder);

    private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(NationRole.values()).map(NationRole::getSerializedName), builder);

    private NationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("nation")
                .then(literal("create").then(argument("name", greedyString())
                        .executes(c -> run(c, NationManager.create(player(c), getString(c, "name"))))))
                .then(literal("info")
                        .executes(c -> info(c, ownNation(c.getSource()), "nation.error.not_in_nation"))
                        .then(argument("name", greedyString()).suggests(NATIONS)
                                .executes(c -> info(c, NationSavedData.get(c.getSource().getServer()).byName(getString(c, "name")),
                                        "nation.error.unknown_nation", getString(c, "name")))))
                .then(literal("list").executes(NationCommands::list))
                .then(literal("invite").then(argument("player", EntityArgument.player())
                        .executes(c -> run(c, NationManager.invite(player(c), EntityArgument.getPlayer(c, "player"))))))
                .then(literal("join").then(argument("name", greedyString()).suggests(INVITES)
                        .executes(c -> run(c, NationManager.join(player(c), getString(c, "name"))))))
                .then(literal("leave").executes(c -> run(c, NationManager.leave(player(c)))))
                .then(literal("kick").then(argument("player", GameProfileArgument.gameProfile()).suggests(MEMBERS)
                        .executes(c -> run(c, NationManager.kick(player(c), profile(c))))))
                .then(literal("promote").then(argument("player", GameProfileArgument.gameProfile()).suggests(MEMBERS)
                        .then(argument("role", word()).suggests(ROLES)
                                .executes(c -> run(c, NationManager.setRole(player(c), profile(c), getString(c, "role")))))))
                .then(literal("disband")
                        .executes(c -> run(c, NationManager.requestDisband(player(c))))
                        .then(literal("confirm").executes(c -> run(c, NationManager.disband(player(c))))))
                .then(literal("motto").then(argument("text", greedyString())
                        .executes(c -> run(c, NationManager.setMotto(player(c), getString(c, "text"))))))
                .then(literal("city")
                        .then(literal("add").then(argument("name", greedyString())
                                .executes(c -> run(c, NationManager.addCity(player(c), getString(c, "name"))))))
                        .then(literal("remove").then(argument("name", greedyString()).suggests(CITIES)
                                .executes(c -> run(c, NationManager.removeCity(player(c), getString(c, "name")))))))
                .then(literal("capital").then(argument("city", greedyString()).suggests(CITIES)
                        .executes(c -> run(c, NationManager.setCapital(player(c), getString(c, "city"))))))
                .then(literal("claim")
                        .executes(c -> run(c, TerritoryManager.claim(player(c), 0)))
                        .then(argument("radius", IntegerArgumentType.integer(0, TerritoryManager.MAX_CLAIM_RADIUS))
                                .executes(c -> run(c, TerritoryManager.claim(player(c), IntegerArgumentType.getInteger(c, "radius"))))))
                .then(literal("unclaim").executes(c -> run(c, TerritoryManager.unclaim(player(c))))));
    }

    /** Envoie le message du résultat à l'auteur ; renvoie 1 en cas de succès, 0 sinon. */
    public static int run(CommandContext<CommandSourceStack> ctx, ActionResult result) {
        CommandSourceStack source = ctx.getSource();
        result.message().ifPresent(message -> {
            if (result.success()) source.sendSuccess(() -> message, false);
            else source.sendFailure(message);
        });
        return result.success() ? 1 : 0;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, Optional<Nation> found, String errorKey, Object... errorArgs) {
        CommandSourceStack source = ctx.getSource();
        if (found.isEmpty()) {
            source.sendFailure(Lang.tr(errorKey, errorArgs));
            return 0;
        }
        Nation nation = found.get();
        MinecraftServer server = source.getServer();
        Component none = Lang.tr("none");

        Component leader = nation.leader().<Component>map(id -> Component.literal(NationManager.playerName(server, id))).orElse(none);
        String officers = namesWithRole(server, nation, NationRole.OFFICER);
        String citizens = namesWithRole(server, nation, NationRole.CITIZEN);
        Component capital = nation.capital().<Component>map(c -> Component.literal(c.name())).orElse(none);
        String cities = nation.cities().stream().map(City::name).collect(Collectors.joining(", "));

        List<Component> lines = List.of(
                Lang.tr("nation.info.header", nation.displayName()),
                Lang.tr("nation.info.motto", nation.motto().isEmpty() ? none : Component.literal(nation.motto())),
                Lang.tr("nation.info.controller", nation.controller().displayName()),
                Lang.tr("nation.info.leader", leader),
                Lang.tr("nation.info.officers", officers.isEmpty() ? none : Component.literal(officers)),
                Lang.tr("nation.info.citizens", nation.members().size(), citizens.isEmpty() ? none : Component.literal(citizens)),
                Lang.tr("nation.info.capital", capital),
                Lang.tr("nation.info.cities", nation.cities().size(), cities.isEmpty() ? none : Component.literal(cities)),
                Lang.tr("nation.info.territory", TerritorySavedData.get(server).claimCount(nation.id()), TerritoryManager.maxClaims(nation)),
                Lang.tr("nation.info.founded", DATE.format(Instant.ofEpochMilli(nation.createdAt()))));
        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Collection<Nation> nations = NationSavedData.get(source.getServer()).all();
        if (nations.isEmpty()) {
            source.sendSuccess(() -> Lang.tr("nation.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Lang.tr("nation.list.header", nations.size()), false);
        nations.forEach(n -> source.sendSuccess(() -> Lang.tr("nation.list.entry", n.displayName(), n.members().size()), false));
        return nations.size();
    }

    private static String namesWithRole(MinecraftServer server, Nation nation, NationRole role) {
        return nation.members().entrySet().stream()
                .filter(e -> e.getValue() == role)
                .map(Map.Entry::getKey)
                .map(id -> NationManager.playerName(server, id))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    private static Optional<Nation> ownNation(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return Optional.empty();
        return NationSavedData.get(source.getServer()).nationOf(player.getUUID());
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        return profiles.iterator().next();
    }
}
