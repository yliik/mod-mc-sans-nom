package com.geopoliticsmod.compat.xaero;

import com.geopoliticsmod.client.ClientTerritoryCache;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.DimensionHighlighterHandler;
import xaero.map.world.MapDimension;

/**
 * Fait redessiner la World Map quand des territoires changent. Xaero met en cache une empreinte
 * par région (512×512 blocs) : on efface celle des régions touchées et de leurs voisines, dont
 * les traits de frontière dépendent (même méthode que la compat Open Parties and Claims de Xaero).
 */
final class WorldMapRefresher implements ClientTerritoryCache.Listener {
    @Override
    public void onChunksChanged(ResourceKey<Level> dimension, long[] chunks) {
        DimensionHighlighterHandler handler = handler(dimension);
        if (handler == null) return;
        LongOpenHashSet regions = new LongOpenHashSet();
        for (long chunk : chunks) {
            int x = ChunkPos.getX(chunk);
            int z = ChunkPos.getZ(chunk);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    regions.add(ChunkPos.asLong((x + dx) >> ClientTerritoryCache.REGION_SHIFT, (z + dz) >> ClientTerritoryCache.REGION_SHIFT));
                }
            }
        }
        regions.forEach(region -> handler.clearCachedHash(ChunkPos.getX(region), ChunkPos.getZ(region)));
    }

    @Override
    public void onReset(Set<ResourceKey<Level>> dimensions) {
        for (ResourceKey<Level> dimension : dimensions) {
            DimensionHighlighterHandler handler = handler(dimension);
            if (handler != null) handler.clearCachedHashes();
        }
    }

    private static DimensionHighlighterHandler handler(ResourceKey<Level> dimension) {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) return null;
        MapProcessor processor = session.getMapProcessor();
        if (processor == null || processor.getMapWorld() == null) return null;
        MapDimension mapDimension = processor.getMapWorld().getDimension(dimension);
        return mapDimension == null ? null : mapDimension.getHighlightHandler();
    }
}
