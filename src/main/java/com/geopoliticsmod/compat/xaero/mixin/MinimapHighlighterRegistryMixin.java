package com.geopoliticsmod.compat.xaero.mixin;

import com.geopoliticsmod.compat.xaero.NationMinimapHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

/** Même principe que {@link WorldMapHighlighterRegistryMixin}, pour le registre propre à Xaero's Minimap. */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class MinimapHighlighterRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void geopoliticsmod$registerNations(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new NationMinimapHighlighter());
    }
}
