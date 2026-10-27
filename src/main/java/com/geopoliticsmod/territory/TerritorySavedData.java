package com.geopoliticsmod.territory;

import com.geopoliticsmod.GeoPoliticsMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Chunks revendiqués par les nations (fichier data/geopoliticsmod_territory.dat de l'Overworld).
 *
 * <p>Deux index tenus à jour ensemble : chunk → nation (recherche en O(1) à chaque bloc cassé,
 * posé ou explosé) et nation → chunks (comptage, contiguïté, synchro). Les chunks sont codés
 * en {@code long} via {@link ChunkPos#toLong()}.
 */
public final class TerritorySavedData extends SavedData {
    private static final String NAME = GeoPoliticsMod.MODID + "_territory";
    private static final int FORMAT = 1;
    private static final Factory<TerritorySavedData> FACTORY = new Factory<>(TerritorySavedData::new, TerritorySavedData::load, null);

    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<UUID>> owners = new HashMap<>();
    private final Map<UUID, Map<ResourceKey<Level>, LongOpenHashSet>> claimsByNation = new HashMap<>();

    public static TerritorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // --- Lecture ---

    @Nullable
    public UUID ownerAt(ResourceKey<Level> dimension, long chunk) {
        Long2ObjectOpenHashMap<UUID> byChunk = owners.get(dimension);
        return byChunk == null ? null : byChunk.get(chunk);
    }

    public int claimCount(UUID nation) {
        return claimsByNation.getOrDefault(nation, Map.of()).values().stream().mapToInt(LongOpenHashSet::size).sum();
    }

    public int claimCount(UUID nation, ResourceKey<Level> dimension) {
        LongOpenHashSet chunks = claimsByNation.getOrDefault(nation, Map.of()).get(dimension);
        return chunks == null ? 0 : chunks.size();
    }

    /** Vrai si l'un des 4 chunks voisins (par un côté) appartient à la nation. */
    public boolean hasAdjacentClaim(UUID nation, ResourceKey<Level> dimension, long chunk) {
        int x = ChunkPos.getX(chunk);
        int z = ChunkPos.getZ(chunk);
        return nation.equals(ownerAt(dimension, ChunkPos.asLong(x + 1, z)))
                || nation.equals(ownerAt(dimension, ChunkPos.asLong(x - 1, z)))
                || nation.equals(ownerAt(dimension, ChunkPos.asLong(x, z + 1)))
                || nation.equals(ownerAt(dimension, ChunkPos.asLong(x, z - 1)));
    }

    /** Vue en lecture seule : nation → dimension → chunks. */
    public Map<UUID, Map<ResourceKey<Level>, LongOpenHashSet>> claimsByNation() {
        return Collections.unmodifiableMap(claimsByNation);
    }

    // --- Mutations (réservées à TerritoryManager) ---

    /** Attribue le chunk à la nation, en le retirant à son ancien propriétaire. Renvoie l'ancien propriétaire. */
    @Nullable
    UUID claim(ResourceKey<Level> dimension, long chunk, UUID nation) {
        UUID previous = owners.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>()).put(chunk, nation);
        if (previous != null && !previous.equals(nation)) removeFromNation(previous, dimension, chunk);
        claimsByNation.computeIfAbsent(nation, k -> new HashMap<>()).computeIfAbsent(dimension, k -> new LongOpenHashSet()).add(chunk);
        setDirty();
        return previous;
    }

    @Nullable
    UUID unclaim(ResourceKey<Level> dimension, long chunk) {
        Long2ObjectOpenHashMap<UUID> byChunk = owners.get(dimension);
        UUID previous = byChunk == null ? null : byChunk.remove(chunk);
        if (previous != null) {
            removeFromNation(previous, dimension, chunk);
            setDirty();
        }
        return previous;
    }

    /** Retire tous les chunks d'une nation. Renvoie les chunks libérés, par dimension. */
    Map<ResourceKey<Level>, long[]> removeNation(UUID nation) {
        Map<ResourceKey<Level>, LongOpenHashSet> claims = claimsByNation.remove(nation);
        if (claims == null) return Map.of();
        Map<ResourceKey<Level>, long[]> released = new HashMap<>();
        claims.forEach((dimension, chunks) -> {
            Long2ObjectOpenHashMap<UUID> byChunk = owners.get(dimension);
            if (byChunk != null) chunks.forEach(byChunk::remove);
            released.put(dimension, chunks.toLongArray());
        });
        setDirty();
        return released;
    }

    private void removeFromNation(UUID nation, ResourceKey<Level> dimension, long chunk) {
        Map<ResourceKey<Level>, LongOpenHashSet> claims = claimsByNation.get(nation);
        if (claims == null) return;
        LongOpenHashSet chunks = claims.get(dimension);
        if (chunks == null) return;
        chunks.remove(chunk);
        if (chunks.isEmpty()) claims.remove(dimension);
        if (claims.isEmpty()) claimsByNation.remove(nation);
    }

    // --- Persistance ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag list = new ListTag();
        claimsByNation.forEach((nation, claims) -> claims.forEach((dimension, chunks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("nation", nation);
            entry.putString("dimension", dimension.location().toString());
            entry.putLongArray("chunks", chunks.toLongArray());
            list.add(entry);
        }));
        tag.put("claims", list);
        return tag;
    }

    private static TerritorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerritorySavedData data = new TerritorySavedData();
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimensionId == null || !entry.hasUUID("nation")) {
                GeoPoliticsMod.LOGGER.error("Entrée de territoire illisible ignorée : {}", entry);
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            UUID nation = entry.getUUID("nation");
            for (long chunk : entry.getLongArray("chunks")) data.claim(dimension, chunk, nation);
        }
        data.setDirty(false);
        return data;
    }
}
