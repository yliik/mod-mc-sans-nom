package com.geopoliticsmod.network;

import com.geopoliticsmod.GeoPoliticsMod;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur → client, à la connexion : tous les territoires (remplace ceux du client). */
public record TerritorySnapshotPayload(List<TerritoryEntry> entries) implements CustomPacketPayload {
    public static final Type<TerritorySnapshotPayload> TYPE = new Type<>(GeoPoliticsMod.id("territory_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, TerritorySnapshotPayload> STREAM_CODEC =
            TerritoryEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(TerritorySnapshotPayload::new, TerritorySnapshotPayload::entries);

    @Override
    public Type<TerritorySnapshotPayload> type() {
        return TYPE;
    }
}
