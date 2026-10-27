package com.geopoliticsmod.nation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Ville d'une nation, fondée à une position. La capitale est l'une d'elles. */
public record City(UUID id, String name, ResourceKey<Level> dimension, BlockPos center, long foundedAt) {
    public static final Codec<City> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(City::id),
            Codec.STRING.fieldOf("name").forGetter(City::name),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(City::dimension),
            BlockPos.CODEC.fieldOf("center").forGetter(City::center),
            Codec.LONG.fieldOf("founded_at").forGetter(City::foundedAt)
    ).apply(i, City::new));
}
