package com.geopoliticsmod.nation;

import com.geopoliticsmod.api.event.NationDisbandedEvent;
import com.geopoliticsmod.core.ActionResult;
import com.geopoliticsmod.core.Lang;
import com.geopoliticsmod.network.NationSync;
import com.geopoliticsmod.territory.TerritoryManager;
import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Règles métier des nations : permissions par rang, validations, notifications et
 * synchronisation. Toutes les méthodes s'exécutent sur le thread serveur.
 */
public final class NationManager {
    /** 3 à 32 caractères : lettres (accents compris), chiffres, espaces, apostrophes, tirets. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} '\\-]{1,30}[\\p{L}\\p{N}]");
    private static final int MOTTO_MAX = 64;
    private static final long INVITE_DURATION_MS = 5 * 60 * 1000;
    public static final int INVITE_PURGE_INTERVAL = 20 * 30;

    /** Invitations en attente : joueur invité → (nation → expiration en ms). Non persistées. */
    private static final Map<UUID, Map<UUID, Long>> INVITES = new HashMap<>();

    private NationManager() {}

    // --- Fondation et appartenance ---

    public static ActionResult create(ServerPlayer founder, String rawName) {
        MinecraftServer server = founder.server;
        NationSavedData data = NationSavedData.get(server);
        Optional<Nation> current = data.nationOf(founder.getUUID());
        if (current.isPresent()) return ActionResult.fail("nation.error.already_in_nation", current.get().displayName());

        String name = normalize(rawName);
        if (!NAME_PATTERN.matcher(name).matches()) return ActionResult.fail("nation.error.invalid_name");
        if (data.byName(name).isPresent()) return ActionResult.fail("nation.error.name_taken", name);

        Nation nation = data.create(name, NationController.PLAYER, founder.getUUID(), colorFor(name), System.currentTimeMillis());
        INVITES.remove(founder.getUUID());
        server.getPlayerList().broadcastSystemMessage(
                Lang.tr("nation.created_broadcast", founder.getDisplayName(), nation.displayName()), false);
        NationSync.broadcastList(server);
        NationSync.sendMembership(server, founder.getUUID());
        return ActionResult.ok("nation.created", nation.displayName());
    }

    public static ActionResult invite(ServerPlayer actor, ServerPlayer target) {
        NationSavedData data = NationSavedData.get(actor.server);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!hasRole(nation, actor.getUUID(), NationRole.OFFICER)) return ActionResult.fail("nation.error.no_permission");
        if (target.getUUID().equals(actor.getUUID())) return ActionResult.fail("nation.error.cannot_target_self");
        if (data.nationOf(target.getUUID()).isPresent()) {
            return ActionResult.fail("nation.error.target_in_nation", target.getDisplayName());
        }

        INVITES.computeIfAbsent(target.getUUID(), k -> new HashMap<>())
                .put(nation.id(), System.currentTimeMillis() + INVITE_DURATION_MS);
        String command = "/nation join " + nation.name();
        target.sendSystemMessage(Lang.tr("nation.invite_received", actor.getDisplayName(), nation.displayName(),
                Component.literal(command).withStyle(style -> style
                        .withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))));
        return ActionResult.ok("nation.invite_sent", target.getDisplayName());
    }

    public static ActionResult join(ServerPlayer player, String nationName) {
        MinecraftServer server = player.server;
        NationSavedData data = NationSavedData.get(server);
        Optional<Nation> current = data.nationOf(player.getUUID());
        if (current.isPresent()) return ActionResult.fail("nation.error.already_in_nation", current.get().displayName());
        Optional<Nation> found = data.byName(normalize(nationName));
        if (found.isEmpty()) return ActionResult.fail("nation.error.unknown_nation", nationName);
        Nation nation = found.get();

        Long expiry = INVITES.getOrDefault(player.getUUID(), Map.of()).get(nation.id());
        if (expiry == null || expiry < System.currentTimeMillis()) {
            return ActionResult.fail("nation.error.no_invite", nation.displayName());
        }

        INVITES.remove(player.getUUID());
        data.putMember(nation, player.getUUID(), NationRole.CITIZEN);
        notifyMembers(server, nation, Lang.tr("nation.member_joined", player.getDisplayName()), player.getUUID());
        NationSync.broadcastList(server);
        NationSync.sendMembership(server, player.getUUID());
        return ActionResult.ok("nation.joined", nation.displayName());
    }

    public static ActionResult leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        NationSavedData data = NationSavedData.get(server);
        Optional<Nation> found = data.nationOf(player.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();

        if (hasRole(nation, player.getUUID(), NationRole.LEADER)) {
            if (nation.members().size() > 1) return ActionResult.fail("nation.error.leader_must_transfer");
            disband(server, nation);
            return ActionResult.ok("nation.left", nation.displayName());
        }

        data.removeMember(nation, player.getUUID());
        notifyMembers(server, nation, Lang.tr("nation.member_left", player.getDisplayName()), null);
        NationSync.broadcastList(server);
        NationSync.sendMembership(server, player.getUUID());
        return ActionResult.ok("nation.left", nation.displayName());
    }

    public static ActionResult kick(ServerPlayer actor, GameProfile target) {
        MinecraftServer server = actor.server;
        NationSavedData data = NationSavedData.get(server);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        NationRole actorRole = nation.roleOf(actor.getUUID()).orElseThrow();
        if (!actorRole.isAtLeast(NationRole.OFFICER)) return ActionResult.fail("nation.error.no_permission");
        if (target.getId().equals(actor.getUUID())) return ActionResult.fail("nation.error.cannot_target_self");
        Optional<NationRole> targetRole = nation.roleOf(target.getId());
        if (targetRole.isEmpty()) return ActionResult.fail("nation.error.not_member", target.getName());
        if (!actorRole.outranks(targetRole.get())) return ActionResult.fail("nation.error.target_rank");

        data.removeMember(nation, target.getId());
        notifyMembers(server, nation, Lang.tr("nation.kicked", target.getName()), actor.getUUID());
        ServerPlayer kicked = server.getPlayerList().getPlayer(target.getId());
        if (kicked != null) kicked.sendSystemMessage(Lang.tr("nation.you_were_kicked", nation.displayName()));
        NationSync.broadcastList(server);
        NationSync.sendMembership(server, target.getId());
        return ActionResult.ok("nation.kicked", target.getName());
    }

    /** Change le rang d'un membre. Nommer un autre dirigeant transmet la direction (l'ancien devient haut gradé). */
    public static ActionResult setRole(ServerPlayer actor, GameProfile target, String roleName) {
        MinecraftServer server = actor.server;
        NationSavedData data = NationSavedData.get(server);
        Optional<NationRole> role = NationRole.parse(roleName);
        if (role.isEmpty()) return ActionResult.fail("nation.error.unknown_role", roleName);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!hasRole(nation, actor.getUUID(), NationRole.LEADER)) return ActionResult.fail("nation.error.no_permission");
        if (target.getId().equals(actor.getUUID())) return ActionResult.fail("nation.error.cannot_target_self");
        if (nation.roleOf(target.getId()).isEmpty()) return ActionResult.fail("nation.error.not_member", target.getName());

        data.putMember(nation, target.getId(), role.get());
        Component message;
        if (role.get() == NationRole.LEADER) {
            data.putMember(nation, actor.getUUID(), NationRole.OFFICER);
            NationSync.sendMembership(server, actor.getUUID());
            message = Lang.tr("nation.leadership_transferred", target.getName(), nation.displayName());
        } else {
            message = Lang.tr("nation.role_changed", target.getName(), role.get().displayName());
        }
        notifyMembers(server, nation, message, actor.getUUID());
        NationSync.sendMembership(server, target.getId());
        return new ActionResult(true, message);
    }

    // --- Dissolution ---

    public static ActionResult requestDisband(ServerPlayer actor) {
        Optional<Nation> found = NationSavedData.get(actor.server).nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        if (!hasRole(found.get(), actor.getUUID(), NationRole.LEADER)) return ActionResult.fail("nation.error.no_permission");
        return ActionResult.ok("nation.disband_confirm", found.get().displayName());
    }

    public static ActionResult disband(ServerPlayer actor) {
        Optional<Nation> found = NationSavedData.get(actor.server).nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        if (!hasRole(found.get(), actor.getUUID(), NationRole.LEADER)) return ActionResult.fail("nation.error.no_permission");
        disband(actor.server, found.get());
        return ActionResult.silent();
    }

    /** Dissout une nation sans contrôle de permission (dirigeant ou administrateur). */
    public static void disband(MinecraftServer server, Nation nation) {
        List<UUID> members = List.copyOf(nation.members().keySet());
        NationSavedData.get(server).remove(nation);
        INVITES.values().forEach(byNation -> byNation.remove(nation.id()));
        NeoForge.EVENT_BUS.post(new NationDisbandedEvent(server, nation));
        server.getPlayerList().broadcastSystemMessage(Lang.tr("nation.disbanded", nation.displayName()), false);
        NationSync.broadcastList(server);
        members.forEach(member -> NationSync.sendMembership(server, member));
    }

    // --- Identité, villes et capitale ---

    public static ActionResult setMotto(ServerPlayer actor, String motto) {
        NationSavedData data = NationSavedData.get(actor.server);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!hasRole(nation, actor.getUUID(), NationRole.LEADER)) return ActionResult.fail("nation.error.no_permission");
        String text = motto.trim();
        if (text.length() > MOTTO_MAX) return ActionResult.fail("nation.error.invalid_motto", MOTTO_MAX);

        data.setMotto(nation, text);
        NationSync.broadcastList(actor.server);
        return ActionResult.ok("nation.motto_set", text);
    }

    public static ActionResult addCity(ServerPlayer actor, String rawName) {
        NationSavedData data = NationSavedData.get(actor.server);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!hasRole(nation, actor.getUUID(), NationRole.OFFICER)) return ActionResult.fail("nation.error.no_permission");
        String name = normalize(rawName);
        if (!NAME_PATTERN.matcher(name).matches()) return ActionResult.fail("nation.error.invalid_city_name");
        if (nation.city(name).isPresent()) return ActionResult.fail("nation.error.city_exists", name);
        if (!TerritoryManager.isOwnedBy(actor.server, nation.id(), actor.level().dimension(), actor.chunkPosition().toLong())) {
            return ActionResult.fail("territory.error.city_outside");
        }

        City city = new City(UUID.randomUUID(), name, actor.level().dimension(), actor.blockPosition(), System.currentTimeMillis());
        data.addCity(nation, city);
        boolean capital = nation.capital().isEmpty();
        if (capital) data.setCapital(nation, city);
        notifyMembers(actor.server, nation, Lang.tr("nation.city_added", name, formatPos(city)), actor.getUUID());
        return capital
                ? ActionResult.ok("nation.city_added_capital", name, formatPos(city))
                : ActionResult.ok("nation.city_added", name, formatPos(city));
    }

    public static ActionResult removeCity(ServerPlayer actor, String cityName) {
        NationSavedData data = NationSavedData.get(actor.server);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!hasRole(nation, actor.getUUID(), NationRole.OFFICER)) return ActionResult.fail("nation.error.no_permission");
        Optional<City> city = nation.city(normalize(cityName));
        if (city.isEmpty()) return ActionResult.fail("nation.error.unknown_city", cityName);
        boolean isCapital = nation.capital().map(c -> c.id().equals(city.get().id())).orElse(false);
        if (isCapital && nation.cities().size() > 1) return ActionResult.fail("nation.error.capital_remove");

        data.removeCity(nation, city.get());
        if (isCapital) data.setCapital(nation, null);
        notifyMembers(actor.server, nation, Lang.tr("nation.city_removed", city.get().name()), actor.getUUID());
        return ActionResult.ok("nation.city_removed", city.get().name());
    }

    public static ActionResult setCapital(ServerPlayer actor, String cityName) {
        NationSavedData data = NationSavedData.get(actor.server);
        Optional<Nation> found = data.nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!hasRole(nation, actor.getUUID(), NationRole.LEADER)) return ActionResult.fail("nation.error.no_permission");
        Optional<City> city = nation.city(normalize(cityName));
        if (city.isEmpty()) return ActionResult.fail("nation.error.unknown_city", cityName);

        data.setCapital(nation, city.get());
        notifyMembers(actor.server, nation, Lang.tr("nation.capital_set", city.get().name()), actor.getUUID());
        return ActionResult.ok("nation.capital_set", city.get().name());
    }

    // --- Invitations ---

    /** Noms des nations qui ont invité ce joueur (pour l'autocomplétion). */
    public static List<String> pendingInvites(MinecraftServer server, UUID player) {
        NationSavedData data = NationSavedData.get(server);
        long now = System.currentTimeMillis();
        return INVITES.getOrDefault(player, Map.of()).entrySet().stream()
                .filter(e -> e.getValue() >= now)
                .flatMap(e -> data.get(e.getKey()).stream())
                .map(Nation::name)
                .toList();
    }

    /** Tâche planifiée : retire les invitations expirées. */
    public static void purgeExpiredInvites(MinecraftServer server) {
        long now = System.currentTimeMillis();
        INVITES.values().forEach(byNation -> byNation.values().removeIf(expiry -> expiry < now));
        INVITES.values().removeIf(Map::isEmpty);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        INVITES.clear();
    }

    // --- Utilitaires ---

    /** Nom d'un joueur, même hors ligne (cache des profils du serveur). */
    public static String playerName(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) return online.getGameProfile().getName();
        return Optional.ofNullable(server.getProfileCache())
                .flatMap(cache -> cache.get(player))
                .map(GameProfile::getName)
                .orElse(player.toString().substring(0, 8));
    }

    public static boolean hasRole(Nation nation, UUID player, NationRole minimum) {
        return nation.roleOf(player).map(role -> role.isAtLeast(minimum)).orElse(false);
    }

    private static void notifyMembers(MinecraftServer server, Nation nation, Component message, UUID except) {
        for (UUID member : nation.members().keySet()) {
            if (member.equals(except)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    private static String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static int colorFor(String name) {
        float hue = (name.toLowerCase(Locale.ROOT).hashCode() & 0xFFFF) / 65535f;
        return Mth.hsvToRgb(hue, 0.65f, 0.9f);
    }

    private static String formatPos(City city) {
        return city.center().getX() + " " + city.center().getY() + " " + city.center().getZ();
    }
}
