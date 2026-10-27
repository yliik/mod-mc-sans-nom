package com.geopoliticsmod.network;

import com.geopoliticsmod.territory.TerritorySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Envoi des territoires aux clients : instantané complet à la connexion, puis des deltas. */
public final class TerritorySync {
    private TerritorySync() {}

    public static void broadcastDelta(ResourceKey<Level> dimension, @Nullable UUID owner, long[] chunks) {
        if (chunks.length == 0) return;
        PacketDistributor.sendToAllPlayers(new ClaimDeltaPayload(dimension, Optional.ofNullable(owner), chunks));
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<TerritoryEntry> entries = new ArrayList<>();
        TerritorySavedData.get(player.server).claimsByNation().forEach((nation, claims) ->
                claims.forEach((dimension, chunks) -> entries.add(new TerritoryEntry(dimension, nation, chunks.toLongArray()))));
        PacketDistributor.sendToPlayer(player, new TerritorySnapshotPayload(entries));
    }
}
