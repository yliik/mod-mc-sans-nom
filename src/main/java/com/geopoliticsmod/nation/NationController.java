package com.geopoliticsmod.nation;

import com.geopoliticsmod.core.Lang;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Qui dirige la nation : des joueurs, ou l'IA des nations PNJ (phase 11). */
public enum NationController implements StringRepresentable {
    PLAYER,
    NPC;

    public static final Codec<NationController> CODEC = StringRepresentable.fromEnum(NationController::values);

    public Component displayName() {
        return Lang.tr("controller." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
