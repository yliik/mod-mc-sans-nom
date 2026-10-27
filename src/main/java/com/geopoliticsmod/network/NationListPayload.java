package com.geopoliticsmod.network;

import com.geopoliticsmod.GeoPoliticsMod;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur → client : liste complète des nations (remplace celle du client). */
public record NationListPayload(List<NationSummary> nations) implements CustomPacketPayload {
    public static final Type<NationListPayload> TYPE = new Type<>(GeoPoliticsMod.id("nation_list"));
    public static final StreamCodec<ByteBuf, NationListPayload> STREAM_CODEC =
            NationSummary.STREAM_CODEC.apply(ByteBufCodecs.list()).map(NationListPayload::new, NationListPayload::nations);

    @Override
    public Type<NationListPayload> type() {
        return TYPE;
    }
}
