package com.geopoliticsmod.network;

import com.geopoliticsmod.GeoPoliticsMod;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Serveur → client : ces chunks appartiennent désormais à {@code owner} (vide = libérés). */
public record ClaimDeltaPayload(ResourceKey<Level> dimension, Optional<UUID> owner, long[] chunks) implements CustomPacketPayload {
    public static final Type<ClaimDeltaPayload> TYPE = new Type<>(GeoPoliticsMod.id("claim_delta"));
    public static final StreamCodec<FriendlyByteBuf, ClaimDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), ClaimDeltaPayload::dimension,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), ClaimDeltaPayload::owner,
            TerritoryEntry.LONG_ARRAY, ClaimDeltaPayload::chunks,
            ClaimDeltaPayload::new);

    @Override
    public Type<ClaimDeltaPayload> type() {
        return TYPE;
    }
}
