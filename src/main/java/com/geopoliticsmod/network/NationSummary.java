package com.geopoliticsmod.network;

import com.geopoliticsmod.nation.Nation;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Vue publique d'une nation, envoyée à tous les clients. */
public record NationSummary(UUID id, String name, String motto, int color, int memberCount) {
    public static final StreamCodec<ByteBuf, NationSummary> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NationSummary::id,
            ByteBufCodecs.STRING_UTF8, NationSummary::name,
            ByteBufCodecs.STRING_UTF8, NationSummary::motto,
            ByteBufCodecs.INT, NationSummary::color,
            ByteBufCodecs.VAR_INT, NationSummary::memberCount,
            NationSummary::new);

    public static NationSummary of(Nation nation) {
        return new NationSummary(nation.id(), nation.name(), nation.motto(), nation.color(), nation.members().size());
    }
}
