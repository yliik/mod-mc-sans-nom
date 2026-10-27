package com.geopoliticsmod.core;

import com.geopoliticsmod.GeoPoliticsMod;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

/** Textes traduisibles du mod : clés {@code geopoliticsmod.<clé>} dans assets/.../lang. */
public final class Lang {
    private Lang() {}

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(GeoPoliticsMod.MODID + "." + key, args);
    }
}
