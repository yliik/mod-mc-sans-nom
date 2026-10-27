package com.geopoliticsmod;

import com.geopoliticsmod.client.ClientNationCache;
import com.geopoliticsmod.client.ClientTerritoryCache;
import com.geopoliticsmod.compat.xaero.XaeroCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Point d'entrée client uniquement : jamais chargé sur un serveur dédié. */
@Mod(value = GeoPoliticsMod.MODID, dist = Dist.CLIENT)
public final class GeoPoliticsModClient {
    public GeoPoliticsModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        XaeroCompat.init();
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientNationCache.clear();
            ClientTerritoryCache.clear();
        });
    }
}
