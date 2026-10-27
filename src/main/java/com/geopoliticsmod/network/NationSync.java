package com.geopoliticsmod.network;

import com.geopoliticsmod.nation.Nation;
import com.geopoliticsmod.nation.NationSavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Envoi des données de nation aux clients. */
public final class NationSync {
    private NationSync() {}

    public static void broadcastList(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(listPayload(server));
    }

    /** Renvoie sa nation et son rang au joueur, s'il est connecté. */
    public static void sendMembership(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, membershipPayload(server, playerId));
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, listPayload(player.server), membershipPayload(player.server, player.getUUID()));
        }
    }

    private static NationListPayload listPayload(MinecraftServer server) {
        return new NationListPayload(NationSavedData.get(server).all().stream().map(NationSummary::of).toList());
    }

    private static PlayerNationPayload membershipPayload(MinecraftServer server, UUID playerId) {
        Optional<Nation> nation = NationSavedData.get(server).nationOf(playerId);
        return new PlayerNationPayload(nation.map(Nation::id), nation.flatMap(n -> n.roleOf(playerId)));
    }
}
