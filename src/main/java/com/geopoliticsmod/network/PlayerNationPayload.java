package com.geopoliticsmod.network;

import com.geopoliticsmod.GeoPoliticsMod;
import com.geopoliticsmod.nation.NationRole;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur → client : nation et rang du joueur (vides s'il n'a pas de nation). */
public record PlayerNationPayload(Optional<UUID> nation, Optional<NationRole> role) implements CustomPacketPayload {
    public static final Type<PlayerNationPayload> TYPE = new Type<>(GeoPoliticsMod.id("player_nation"));
    public static final StreamCodec<ByteBuf, PlayerNationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), PlayerNationPayload::nation,
            ByteBufCodecs.optional(NationRole.STREAM_CODEC), PlayerNationPayload::role,
            PlayerNationPayload::new);

    @Override
    public Type<PlayerNationPayload> type() {
        return TYPE;
    }
}
