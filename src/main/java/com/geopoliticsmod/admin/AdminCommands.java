package com.geopoliticsmod.admin;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.geopoliticsmod.GeoPoliticsMod;
import com.geopoliticsmod.core.Lang;
import com.geopoliticsmod.nation.Nation;
import com.geopoliticsmod.nation.NationManager;
import com.geopoliticsmod.nation.NationSavedData;
import com.geopoliticsmod.territory.ClaimProtection;
import com.geopoliticsmod.territory.TerritoryManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Commande opérateur {@code /geoadmin} (niveau de permission 2). Elle s'enrichit à chaque phase ;
 * l'écran d'administration complet arrive en phase 13. Chaque modification est journalisée.
 */
public final class AdminCommands {
    private static final SuggestionProvider<CommandSourceStack> NATIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            NationSavedData.get(ctx.getSource().getServer()).all().stream().map(Nation::name), builder);

    private AdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("geoadmin")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("bypass").executes(AdminCommands::toggleBypass))
                .then(literal("nation")
                        .then(literal("disband").then(argument("name", greedyString()).suggests(NATIONS)
                                .executes(AdminCommands::disbandNation))))
                .then(literal("territory")
                        .then(literal("claim").then(argument("name", greedyString()).suggests(NATIONS)
                                .executes(AdminCommands::claimChunk)))
                        .then(literal("unclaim").executes(AdminCommands::unclaimChunk))
                        .then(literal("info").executes(AdminCommands::chunkInfo))));
    }

    private static int toggleBypass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = ClaimProtection.toggleBypass(player.getUUID());
        log(ctx, "bypass " + (enabled ? "activé" : "désactivé"));
        ctx.getSource().sendSuccess(() -> Lang.tr(enabled ? "admin.bypass_on" : "admin.bypass_off"), true);
        return 1;
    }

    private static int disbandNation(CommandContext<CommandSourceStack> ctx) {
        Optional<Nation> nation = nationArgument(ctx);
        if (nation.isEmpty()) return 0;
        NationManager.disband(ctx.getSource().getServer(), nation.get());
        log(ctx, "dissolution de " + describe(nation.get()));
        ctx.getSource().sendSuccess(() -> Lang.tr("admin.nation_disbanded", nation.get().displayName()), true);
        return 1;
    }

    private static int claimChunk(CommandContext<CommandSourceStack> ctx) {
        Optional<Nation> nation = nationArgument(ctx);
        if (nation.isEmpty()) return 0;
        CommandSourceStack source = ctx.getSource();
        ChunkPos chunk = sourceChunk(source);
        UUID previous = TerritoryManager.forceClaim(source.getServer(), source.getLevel().dimension(), chunk.toLong(), nation.get().id());
        log(ctx, "chunk " + chunk + " (" + source.getLevel().dimension().location() + ") attribué à " + describe(nation.get())
                + (previous == null ? "" : ", ancien propriétaire " + previous));
        source.sendSuccess(() -> Lang.tr("admin.territory.claimed", chunk.x, chunk.z, nation.get().displayName()), true);
        return 1;
    }

    private static int unclaimChunk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ChunkPos chunk = sourceChunk(source);
        UUID previous = TerritoryManager.forceUnclaim(source.getServer(), source.getLevel().dimension(), chunk.toLong());
        if (previous == null) {
            source.sendFailure(Lang.tr("admin.territory.not_claimed", chunk.x, chunk.z));
            return 0;
        }
        log(ctx, "chunk " + chunk + " (" + source.getLevel().dimension().location() + ") libéré, ancien propriétaire " + previous);
        source.sendSuccess(() -> Lang.tr("admin.territory.unclaimed", chunk.x, chunk.z), true);
        return 1;
    }

    private static int chunkInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunk = new ChunkPos(pos);
        Component owner = ClaimProtection.ownerOf(level, pos).<Component>map(Nation::displayName).orElse(Lang.tr("none"));
        source.sendSuccess(() -> Lang.tr("admin.territory.info", chunk.x, chunk.z, level.dimension().location().toString(), owner), false);
        return 1;
    }

    private static Optional<Nation> nationArgument(CommandContext<CommandSourceStack> ctx) {
        String name = getString(ctx, "name");
        Optional<Nation> nation = NationSavedData.get(ctx.getSource().getServer()).byName(name);
        if (nation.isEmpty()) ctx.getSource().sendFailure(Lang.tr("nation.error.unknown_nation", name));
        return nation;
    }

    private static ChunkPos sourceChunk(CommandSourceStack source) {
        return new ChunkPos(BlockPos.containing(source.getPosition()));
    }

    private static String describe(Nation nation) {
        return nation.name() + " (" + nation.id() + ")";
    }

    private static void log(CommandContext<CommandSourceStack> ctx, String action) {
        GeoPoliticsMod.LOGGER.info("[admin] {} : {}", ctx.getSource().getTextName(), action);
    }
}
