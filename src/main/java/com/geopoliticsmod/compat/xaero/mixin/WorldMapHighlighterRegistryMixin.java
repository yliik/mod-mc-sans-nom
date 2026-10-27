package com.geopoliticsmod.compat.xaero.mixin;

import com.geopoliticsmod.compat.xaero.NationWorldMapHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Xaero's World Map crée un registre de surligneurs par session et le verrouille avec
 * {@code end()} : on ajoute le surligneur des nations juste avant le verrouillage.
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class WorldMapHighlighterRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void geopoliticsmod$registerNations(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new NationWorldMapHighlighter());
    }
}
