package com.geopoliticsmod.territory;

import com.geopoliticsmod.GeoPoliticsMod;
import com.geopoliticsmod.api.event.NationDisbandedEvent;
import com.geopoliticsmod.core.ActionResult;
import com.geopoliticsmod.core.config.GeoConfig;
import com.geopoliticsmod.nation.City;
import com.geopoliticsmod.nation.Nation;
import com.geopoliticsmod.nation.NationManager;
import com.geopoliticsmod.nation.NationRole;
import com.geopoliticsmod.nation.NationSavedData;
import com.geopoliticsmod.network.TerritorySync;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

/** Règles de revendication des chunks. Thread serveur uniquement. */
public final class TerritoryManager {
    public static final int MAX_CLAIM_RADIUS = 3;

    private TerritoryManager() {}

    public static int maxClaims(Nation nation) {
        return GeoConfig.BASE_CLAIMS.get() + GeoConfig.CLAIMS_PER_MEMBER.get() * nation.members().size();
    }

    /** Revendique le chunk du joueur ({@code radius} = 0) ou le carré de (2r+1)² chunks centré sur lui. */
    public static ActionResult claim(ServerPlayer actor, int radius) {
        MinecraftServer server = actor.server;
        Optional<Nation> found = NationSavedData.get(server).nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!NationManager.hasRole(nation, actor.getUUID(), NationRole.OFFICER)) return ActionResult.fail("nation.error.no_permission");

        TerritorySavedData territory = TerritorySavedData.get(server);
        ResourceKey<Level> dimension = actor.level().dimension();
        ChunkPos center = actor.chunkPosition();

        // Du centre vers l'extérieur : chaque chunk est examiné après ses voisins plus proches,
        // ce qui satisfait la contiguïté au fil de la boucle.
        List<ChunkPos> area = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) area.add(new ChunkPos(center.x + dx, center.z + dz));
        }
        area.sort(Comparator.<ChunkPos>comparingInt(p -> Math.max(Math.abs(p.x - center.x), Math.abs(p.z - center.z)))
                .thenComparingInt(p -> Math.abs(p.x - center.x) + Math.abs(p.z - center.z)));

        LongArrayList claimed = new LongArrayList();
        ActionResult firstRefusal = null;
        for (ChunkPos pos : area) {
            ActionResult refusal = checkClaim(server, territory, nation, dimension, pos.toLong());
            if (refusal == null) {
                territory.claim(dimension, pos.toLong(), nation.id());
                claimed.add(pos.toLong());
            } else if (firstRefusal == null) {
                firstRefusal = refusal;
            }
        }
        if (claimed.isEmpty()) return firstRefusal;

        TerritorySync.broadcastDelta(dimension, nation.id(), claimed.toLongArray());
        int used = territory.claimCount(nation.id());
        return radius == 0
                ? ActionResult.ok("territory.claimed", center.x, center.z, used, maxClaims(nation))
                : ActionResult.ok("territory.claimed_many", claimed.size(), area.size(), used, maxClaims(nation));
    }

    public static ActionResult unclaim(ServerPlayer actor) {
        MinecraftServer server = actor.server;
        Optional<Nation> found = NationSavedData.get(server).nationOf(actor.getUUID());
        if (found.isEmpty()) return ActionResult.fail("nation.error.not_in_nation");
        Nation nation = found.get();
        if (!NationManager.hasRole(nation, actor.getUUID(), NationRole.OFFICER)) return ActionResult.fail("nation.error.no_permission");

        TerritorySavedData territory = TerritorySavedData.get(server);
        ResourceKey<Level> dimension = actor.level().dimension();
        ChunkPos chunk = actor.chunkPosition();
        if (!nation.id().equals(territory.ownerAt(dimension, chunk.toLong()))) return ActionResult.fail("territory.error.not_owned");
        Optional<City> city = cityIn(nation, dimension, chunk.toLong());
        if (city.isPresent()) return ActionResult.fail("territory.error.city_in_chunk", city.get().name());

        territory.unclaim(dimension, chunk.toLong());
        TerritorySync.broadcastDelta(dimension, null, new long[] {chunk.toLong()});
        return ActionResult.ok("territory.unclaimed", chunk.x, chunk.z, territory.claimCount(nation.id()), maxClaims(nation));
    }

    /** Administration : attribue le chunk à la nation sans aucune règle (le retire à son ancien propriétaire). */
    @Nullable
    public static UUID forceClaim(MinecraftServer server, ResourceKey<Level> dimension, long chunk, UUID nation) {
        UUID previous = TerritorySavedData.get(server).claim(dimension, chunk, nation);
        TerritorySync.broadcastDelta(dimension, nation, new long[] {chunk});
        return previous;
    }

    /** Administration : libère le chunk sans aucune règle. */
    @Nullable
    public static UUID forceUnclaim(MinecraftServer server, ResourceKey<Level> dimension, long chunk) {
        UUID previous = TerritorySavedData.get(server).unclaim(dimension, chunk);
        if (previous != null) TerritorySync.broadcastDelta(dimension, null, new long[] {chunk});
        return previous;
    }

    /** Vrai si la position (dimension + chunk) appartient à la nation : sert à valider les villes. */
    public static boolean isOwnedBy(MinecraftServer server, UUID nation, ResourceKey<Level> dimension, long chunk) {
        return nation.equals(TerritorySavedData.get(server).ownerAt(dimension, chunk));
    }

    public static void onNationDisbanded(NationDisbandedEvent event) {
        TerritorySavedData.get(event.getServer()).removeNation(event.getNation().id())
                .forEach((dimension, chunks) -> TerritorySync.broadcastDelta(dimension, null, chunks));
    }

    /** Au démarrage : libère les chunks de nations qui n'existent plus (sauvegarde incohérente). */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        NationSavedData nations = NationSavedData.get(server);
        TerritorySavedData territory = TerritorySavedData.get(server);
        Set<UUID> orphans = territory.claimsByNation().keySet().stream()
                .filter(id -> nations.get(id).isEmpty())
                .collect(Collectors.toSet());
        for (UUID orphan : orphans) {
            territory.removeNation(orphan);
            GeoPoliticsMod.LOGGER.warn("Territoire de la nation inexistante {} libéré", orphan);
        }
    }

    @Nullable
    private static ActionResult checkClaim(MinecraftServer server, TerritorySavedData territory, Nation nation,
                                           ResourceKey<Level> dimension, long chunk) {
        UUID owner = territory.ownerAt(dimension, chunk);
        if (nation.id().equals(owner)) return ActionResult.fail("territory.error.already_own");
        if (owner != null) {
            String ownerName = NationSavedData.get(server).get(owner).map(Nation::name).orElse("?");
            return ActionResult.fail("territory.error.owned_by_other", ownerName);
        }
        int max = maxClaims(nation);
        if (territory.claimCount(nation.id()) >= max) return ActionResult.fail("territory.error.limit", max);
        if (GeoConfig.REQUIRE_CONTIGUOUS_CLAIMS.get()
                && territory.claimCount(nation.id(), dimension) > 0
                && !territory.hasAdjacentClaim(nation.id(), dimension, chunk)) {
            return ActionResult.fail("territory.error.not_adjacent");
        }
        return null;
    }

    private static Optional<City> cityIn(Nation nation, ResourceKey<Level> dimension, long chunk) {
        return nation.cities().stream()
                .filter(c -> c.dimension().equals(dimension) && ChunkPos.asLong(c.center()) == chunk)
                .findFirst();
    }
}
