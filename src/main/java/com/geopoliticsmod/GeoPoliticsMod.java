package com.geopoliticsmod;

import com.geopoliticsmod.admin.AdminCommands;
import com.geopoliticsmod.core.config.GeoConfig;
import com.geopoliticsmod.core.scheduler.GeoScheduler;
import com.geopoliticsmod.nation.NationCommands;
import com.geopoliticsmod.nation.NationManager;
import com.geopoliticsmod.network.GeoNetwork;
import com.geopoliticsmod.network.NationSync;
import com.geopoliticsmod.network.TerritorySync;
import com.geopoliticsmod.territory.BorderTracker;
import com.geopoliticsmod.territory.ClaimProtection;
import com.geopoliticsmod.territory.TerritoryManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Point d'entrée commun (client + serveur dédié).
 *
 * <p>Le nom du mod n'est pas encore fixé : l'identifiant {@link #MODID} est le seul endroit
 * où « geopoliticsmod » apparaît dans le code Java. Pour renommer le mod, changer
 * {@code mod_id} dans gradle.properties, cette constante, puis renommer le package racine.
 */
@Mod(GeoPoliticsMod.MODID)
public final class GeoPoliticsMod {
    public static final String MODID = "geopoliticsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GeoPoliticsMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, GeoConfig.SERVER_SPEC);
        modEventBus.addListener(GeoNetwork::register);

        NeoForge.EVENT_BUS.addListener(GeoScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(GeoScheduler::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GeoPoliticsMod::registerCommands);
        NeoForge.EVENT_BUS.addListener(NationSync::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(NationManager::onServerStopped);

        NeoForge.EVENT_BUS.addListener(TerritorySync::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(TerritoryManager::onNationDisbanded);
        NeoForge.EVENT_BUS.addListener(TerritoryManager::onServerStarted);
        NeoForge.EVENT_BUS.addListener(BorderTracker::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(BorderTracker::onServerStopped);
        registerProtection();

        GeoScheduler.register("nation_invites_purge", () -> NationManager.INVITE_PURGE_INTERVAL, NationManager::purgeExpiredInvites);
        GeoScheduler.register("border_tracker", GeoConfig.BORDER_CHECK_INTERVAL::getAsInt, BorderTracker::tick);
    }

    private static void registerProtection() {
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onExplosion);
        NeoForge.EVENT_BUS.addListener(ClaimProtection::onServerStopped);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        NationCommands.register(event.getDispatcher());
        AdminCommands.register(event.getDispatcher());
    }

    /** Identifiant namespacé du mod : {@code geopoliticsmod:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
