package com.geopoliticsmod.network;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Chunks d'une nation dans une dimension. */
public record TerritoryEntry(ResourceKey<Level> dimension, UUID nation, long[] chunks) {
    public static final StreamCodec<FriendlyByteBuf, long[]> LONG_ARRAY =
            StreamCodec.of(FriendlyByteBuf::writeLongArray, FriendlyByteBuf::readLongArray);

    public static final StreamCodec<FriendlyByteBuf, TerritoryEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), TerritoryEntry::dimension,
            UUIDUtil.STREAM_CODEC, TerritoryEntry::nation,
            LONG_ARRAY, TerritoryEntry::chunks,
            TerritoryEntry::new);
}
