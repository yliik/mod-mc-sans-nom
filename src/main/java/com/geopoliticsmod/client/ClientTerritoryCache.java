package com.geopoliticsmod.client;

import com.geopoliticsmod.network.ClaimDeltaPayload;
import com.geopoliticsmod.network.TerritorySnapshotPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Copie côté client des territoires (chunk → nation), tenue à jour par deltas.
 *
 * <p>Lue par d'autres threads (rendu des cartes Xaero) : chaque modification publie une
 * <b>nouvelle copie</b> des données de la dimension concernée, jamais modifiée ensuite. Les
 * deltas sont rares (un claim), la copie est donc bon marché à l'échelle du jeu.
 *
 * <p>Comme {@link ClientNationCache}, n'importe aucune classe {@code net.minecraft.client}.
 */
public final class ClientTerritoryCache {
    /** Taille d'une région de carte Xaero, en chunks (512 blocs). */
    public static final int REGION_SHIFT = 5;

    /** Écouteur des changements, appelé sur le thread client principal. */
    public interface Listener {
        void onChunksChanged(ResourceKey<Level> dimension, long[] chunks);

        /** Tout a été remplacé (connexion) pour ces dimensions. */
        void onReset(Set<ResourceKey<Level>> dimensions);
    }

    /** Territoires d'une dimension, immuables une fois publiés. */
    private record DimensionClaims(Long2ObjectOpenHashMap<UUID> owners, LongOpenHashSet regions) {
        static DimensionClaims of(Long2ObjectOpenHashMap<UUID> owners) {
            LongOpenHashSet regions = new LongOpenHashSet();
            owners.keySet().forEach(chunk -> regions.add(ChunkPos.asLong(
                    ChunkPos.getX(chunk) >> REGION_SHIFT, ChunkPos.getZ(chunk) >> REGION_SHIFT)));
            return new DimensionClaims(owners, regions);
        }
    }

    private static volatile Map<ResourceKey<Level>, DimensionClaims> claims = Map.of();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientTerritoryCache() {}

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    @Nullable
    public static UUID ownerAt(ResourceKey<Level> dimension, long chunk) {
        DimensionClaims dimensionClaims = claims.get(dimension);
        return dimensionClaims == null ? null : dimensionClaims.owners().get(chunk);
    }

    @Nullable
    public static UUID ownerAt(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ownerAt(dimension, ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Vrai si la région de carte (32×32 chunks) contient au moins un chunk revendiqué. */
    public static boolean regionHasClaims(ResourceKey<Level> dimension, int regionX, int regionZ) {
        DimensionClaims dimensionClaims = claims.get(dimension);
        return dimensionClaims != null && dimensionClaims.regions().contains(ChunkPos.asLong(regionX, regionZ));
    }

    public static void onSnapshot(TerritorySnapshotPayload payload, IPayloadContext context) {
        Map<ResourceKey<Level>, Long2ObjectOpenHashMap<UUID>> owners = new HashMap<>();
        payload.entries().forEach(entry -> {
            Long2ObjectOpenHashMap<UUID> byChunk = owners.computeIfAbsent(entry.dimension(), k -> new Long2ObjectOpenHashMap<>());
            for (long chunk : entry.chunks()) byChunk.put(chunk, entry.nation());
        });
        Map<ResourceKey<Level>, DimensionClaims> next = new HashMap<>();
        owners.forEach((dimension, byChunk) -> next.put(dimension, DimensionClaims.of(byChunk)));

        Set<ResourceKey<Level>> changed = new HashSet<>(claims.keySet());
        changed.addAll(next.keySet());
        claims = Map.copyOf(next);
        LISTENERS.forEach(listener -> listener.onReset(changed));
    }

    public static void onDelta(ClaimDeltaPayload payload, IPayloadContext context) {
        DimensionClaims current = claims.get(payload.dimension());
        Long2ObjectOpenHashMap<UUID> owners = current == null ? new Long2ObjectOpenHashMap<>() : new Long2ObjectOpenHashMap<>(current.owners());
        UUID owner = payload.owner().orElse(null);
        for (long chunk : payload.chunks()) {
            if (owner == null) owners.remove(chunk);
            else owners.put(chunk, owner);
        }

        Map<ResourceKey<Level>, DimensionClaims> next = new HashMap<>(claims);
        next.put(payload.dimension(), DimensionClaims.of(owners));
        claims = Map.copyOf(next);
        LISTENERS.forEach(listener -> listener.onChunksChanged(payload.dimension(), payload.chunks()));
    }

    public static void clear() {
        claims = Map.of();
    }
}
