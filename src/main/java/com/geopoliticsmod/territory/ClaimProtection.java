package com.geopoliticsmod.territory;

import com.geopoliticsmod.core.Lang;
import com.geopoliticsmod.core.config.GeoConfig;
import com.geopoliticsmod.nation.Nation;
import com.geopoliticsmod.nation.NationSavedData;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Protection des territoires : hors de sa nation, un joueur ne peut ni casser, ni poser, ni
 * utiliser les blocs (portes, coffres, leviers…), ni vider/remplir un seau, ni toucher aux
 * cadres, porte-armures et véhicules à inventaire. Manger, combattre, marcher restent permis.
 *
 * <p>Vérifié côté serveur uniquement (le serveur renvoie l'état réel au client). Chaque test
 * coûte une recherche O(1) dans l'index des chunks. Les guerres (phase 9) lèveront ces
 * protections entre belligérants.
 */
public final class ClaimProtection {
    private static final Set<UUID> BYPASS = new HashSet<>();

    private ClaimProtection() {}

    /** Bascule le mode contournement d'un administrateur. Renvoie le nouvel état. */
    public static boolean toggleBypass(UUID player) {
        if (BYPASS.remove(player)) return false;
        BYPASS.add(player);
        return true;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        BYPASS.clear();
    }

    /** Vrai si le joueur peut modifier le monde à cette position ; sinon le prévient dans la barre d'action. */
    public static boolean canModify(Player player, ServerLevel level, BlockPos pos) {
        UUID owner = TerritorySavedData.get(level.getServer()).ownerAt(level.dimension(), ChunkPos.asLong(pos));
        if (owner == null || BYPASS.contains(player.getUUID())) return true;
        if (player instanceof FakePlayer) return GeoConfig.ALLOW_FAKE_PLAYERS.get();

        NationSavedData nations = NationSavedData.get(level.getServer());
        if (nations.nationOf(player.getUUID()).map(n -> n.id().equals(owner)).orElse(false)) return true;

        nations.get(owner).ifPresent(nation ->
                player.displayClientMessage(Lang.tr("territory.protected", nation.displayName()), true));
        return false;
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !canModify(event.getPlayer(), level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && event.getLevel() instanceof ServerLevel level
                && !canModify(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && !canModify(event.getEntity(), level, event.getPos())) {
            // Bloque l'usage du bloc et l'usage d'un objet SUR le bloc (briquet, os…),
            // mais pas l'usage de l'objet en main (manger, boire).
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        }
    }

    /** Seaux (NeoForge 1.21.1 n'a plus d'événement dédié) : on refait la visée du seau, fluides compris. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getItemStack().getItem() instanceof BucketItem) || !(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        HitResult target = player.pick(player.blockInteractionRange(), 1.0f, true);
        if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) return;
        // Le seau agit sur le bloc visé ou, pour verser, sur le bloc voisin.
        if (!canModify(player, level, hit.getBlockPos()) || !canModify(player, level, hit.getBlockPos().relative(hit.getDirection()))) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isProtectedEntity(event.getTarget()) && event.getLevel() instanceof ServerLevel level
                && !canModify(event.getEntity(), level, event.getTarget().blockPosition())) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isProtectedEntity(event.getTarget()) && event.getLevel() instanceof ServerLevel level
                && !canModify(event.getEntity(), level, event.getTarget().blockPosition())) {
            event.setCanceled(true);
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if ((target instanceof HangingEntity || target instanceof ArmorStand) && target.level() instanceof ServerLevel level
                && !canModify(event.getEntity(), level, target.blockPosition())) {
            event.setCanceled(true);
        }
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!GeoConfig.PROTECT_FROM_EXPLOSIONS.get() || !(event.getLevel() instanceof ServerLevel level)) return;
        TerritorySavedData territory = TerritorySavedData.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos -> territory.ownerAt(level.dimension(), ChunkPos.asLong(pos)) != null);
    }

    private static boolean isProtectedEntity(Entity entity) {
        return entity instanceof HangingEntity || entity instanceof ArmorStand || entity instanceof ContainerEntity;
    }

    /** Nation propriétaire d'une position, s'il y en a une. */
    public static Optional<Nation> ownerOf(ServerLevel level, BlockPos pos) {
        UUID owner = TerritorySavedData.get(level.getServer()).ownerAt(level.dimension(), ChunkPos.asLong(pos));
        return owner == null ? Optional.empty() : NationSavedData.get(level.getServer()).get(owner);
    }
}
