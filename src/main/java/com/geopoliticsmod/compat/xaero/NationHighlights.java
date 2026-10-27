package com.geopoliticsmod.compat.xaero;

import com.geopoliticsmod.client.ClientNationCache;
import com.geopoliticsmod.client.ClientTerritoryCache;
import com.geopoliticsmod.network.NationSummary;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Calculs communs aux surligneurs World Map et Minimap (ils n'ont pas de classe mère commune).
 * Appelé depuis les threads de rendu de Xaero : ne lit que les caches client publiés.
 */
final class NationHighlights {
    /** Opacité du remplissage et des bords (frontières) des chunks revendiqués, sur 255. */
    private static final int FILL_ALPHA = 0x40;
    private static final int BORDER_ALPHA = 0xC0;
    private static final int UNKNOWN_COLOR = 0xFFFFFF;

    private NationHighlights() {}

    /**
     * Couleurs du chunk au format Xaero {@code [centre, nord, est, sud, ouest]}. Un bord prend
     * la couleur de frontière quand le chunk voisin n'appartient pas à la même nation.
     */
    @Nullable
    static int[] colors(ResourceKey<Level> dimension, int chunkX, int chunkZ, int[] out) {
        UUID owner = ClientTerritoryCache.ownerAt(dimension, chunkX, chunkZ);
        if (owner == null) return null;
        int rgb = color(owner);
        int fill = toXaeroColor(rgb, FILL_ALPHA);
        int border = toXaeroColor(rgb, BORDER_ALPHA);
        out[0] = fill;
        out[1] = owner.equals(ClientTerritoryCache.ownerAt(dimension, chunkX, chunkZ - 1)) ? fill : border;
        out[2] = owner.equals(ClientTerritoryCache.ownerAt(dimension, chunkX + 1, chunkZ)) ? fill : border;
        out[3] = owner.equals(ClientTerritoryCache.ownerAt(dimension, chunkX, chunkZ + 1)) ? fill : border;
        out[4] = owner.equals(ClientTerritoryCache.ownerAt(dimension, chunkX - 1, chunkZ)) ? fill : border;
        return out;
    }

    /**
     * Empreinte d'une région de carte : change dès qu'un chunk de la région ou de sa bordure
     * (dont dépendent les traits de frontière) change de propriétaire ou de couleur.
     */
    static int regionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        int size = 1 << ClientTerritoryCache.REGION_SHIFT;
        int minX = regionX << ClientTerritoryCache.REGION_SHIFT;
        int minZ = regionZ << ClientTerritoryCache.REGION_SHIFT;
        int hash = 1;
        for (int x = minX - 1; x <= minX + size; x++) {
            for (int z = minZ - 1; z <= minZ + size; z++) {
                UUID owner = ClientTerritoryCache.ownerAt(dimension, x, z);
                hash = 31 * hash + (owner == null ? 0 : Objects.hash(owner, color(owner)));
            }
        }
        return hash;
    }

    /** Nom de la nation propriétaire, dans sa couleur, pour les infobulles. */
    @Nullable
    static Component name(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        UUID owner = ClientTerritoryCache.ownerAt(dimension, chunkX, chunkZ);
        if (owner == null) return null;
        return ClientNationCache.nation(owner)
                .<Component>map(n -> Component.literal(n.name()).withColor(n.color()))
                .orElse(null);
    }

    private static int color(UUID nation) {
        return ClientNationCache.nation(nation).map(NationSummary::color).orElse(UNKNOWN_COLOR);
    }

    /** RVB {@code 0xRRGGBB} → format Xaero {@code 0xBBGGRRAA}. */
    private static int toXaeroColor(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return b << 24 | g << 16 | r << 8 | alpha;
    }
}
