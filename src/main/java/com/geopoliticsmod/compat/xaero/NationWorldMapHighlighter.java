package com.geopoliticsmod.compat.xaero;

import com.geopoliticsmod.client.ClientTerritoryCache;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

/** Territoires des nations sur Xaero's World Map. Enregistré par {@code WorldMapHighlighterRegistryMixin}. */
public final class NationWorldMapHighlighter extends ChunkHighlighter {
    private final int[] colors = new int[5];

    public NationWorldMapHighlighter() {
        super(true); // affiché aussi hors des zones déjà explorées
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return NationHighlights.regionHash(dimension, regionX, regionZ);
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
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return NationHighlights.name(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return NationHighlights.name(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
        // La Minimap a son propre surligneur (NationMinimapHighlighter) : rien à ajouter ici.
    }
}
