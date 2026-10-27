package com.geopoliticsmod.compat.xaero;

import com.geopoliticsmod.GeoPoliticsMod;
import com.geopoliticsmod.client.ClientTerritoryCache;
import net.neoforged.fml.ModList;

/**
 * Compatibilité Xaero's World Map / Minimap (client uniquement).
 *
 * <p>Les surligneurs sont ajoutés aux registres de Xaero par les mixins de
 * {@code compat.xaero.mixin} (déclarés dans {@code geopoliticsmod.mixins.json}), appliqués
 * seulement si le mod ciblé est présent ({@link XaeroMixinPlugin}). Le package des mixins ne
 * doit contenir que des mixins : Mixin interdit d'y charger d'autres classes.
 *
 * <p>Cette classe ne référence aucune classe Xaero : elle peut être chargée sans Xaero.
 */
public final class XaeroCompat {
    public static final String WORLD_MAP_ID = "xaeroworldmap";
    public static final String MINIMAP_ID = "xaerominimap";

    private XaeroCompat() {}

    public static void init() {
        if (ModList.get().isLoaded(WORLD_MAP_ID)) {
            ClientTerritoryCache.addListener(new WorldMapRefresher());
            GeoPoliticsMod.LOGGER.info("Compatibilité Xaero's World Map activée");
        }
        if (ModList.get().isLoaded(MINIMAP_ID)) {
            GeoPoliticsMod.LOGGER.info("Compatibilité Xaero's Minimap activée");
        }
    }
}
