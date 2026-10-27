package com.geopoliticsmod.compat.xaero;

import com.geopoliticsmod.client.ClientTerritoryCache;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/** Territoires des nations sur Xaero's Minimap. Enregistré par {@code MinimapHighlighterRegistryMixin}. */
public final class NationMinimapHighlighter extends ChunkHighlighter {
    private final int[] colors = new int[5];

    public NationMinimapHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return ClientTerritoryCache.regionHasClaims(dimension, regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ClientTerritoryCache.ownerAt(dimension, chunkX, chunkZ) != null;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return NationHighlights.colors(dimension, chunkX, chunkZ, colors);
    }

    @Override
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX, int chunkZ, int width) {
        Component name = NationHighlights.name(dimension, chunkX, chunkZ);
        if (name != null) compiler.addLine(name);
    }
}
