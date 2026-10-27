package com.geopoliticsmod.nation;

import com.geopoliticsmod.core.Lang;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Rang d'un membre dans sa nation, du plus bas au plus haut. Le titre affiché (Président, Roi…)
 * dépendra du régime (phase 4) ; le rang, lui, fixe les permissions.
 */
public enum NationRole implements StringRepresentable {
    CITIZEN,
    OFFICER,
    LEADER;

    public static final Codec<NationRole> CODEC = StringRepresentable.fromEnum(NationRole::values);
    public static final StreamCodec<ByteBuf, NationRole> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], NationRole::ordinal);

    public boolean isAtLeast(NationRole other) {
        return ordinal() >= other.ordinal();
    }

    public boolean outranks(NationRole other) {
        return ordinal() > other.ordinal();
    }

    public Component displayName() {
        return Lang.tr("role." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<NationRole> parse(String name) {
        return Arrays.stream(values()).filter(r -> r.getSerializedName().equalsIgnoreCase(name)).findFirst();
    }
}
