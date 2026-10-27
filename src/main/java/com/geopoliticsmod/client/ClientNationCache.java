package com.geopoliticsmod.client;

import com.geopoliticsmod.nation.NationRole;
import com.geopoliticsmod.network.NationListPayload;
import com.geopoliticsmod.network.NationSummary;
import com.geopoliticsmod.network.PlayerNationPayload;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Copie côté client des données de nation reçues du serveur. Lecture seule pour les écrans
 * et le HUD ; vidée à la déconnexion.
 *
 * <p>N'importe aucune classe {@code net.minecraft.client} : elle est référencée par
 * l'enregistrement réseau commun et doit pouvoir être chargée sur un serveur dédié.
 */
public final class ClientNationCache {
    // volatile : lus aussi par le thread de rendu des cartes Xaero.
    private static volatile Map<UUID, NationSummary> nations = Map.of();
    private static volatile UUID ownNation;
    private static volatile NationRole ownRole;

    private ClientNationCache() {}

    public static Collection<NationSummary> nations() {
        return nations.values();
    }

    public static Optional<NationSummary> nation(UUID id) {
        return Optional.ofNullable(nations.get(id));
    }

    public static Optional<NationSummary> ownNation() {
        return ownNation == null ? Optional.empty() : nation(ownNation);
    }

    public static Optional<NationRole> ownRole() {
        return Optional.ofNullable(ownRole);
    }

    public static void onNationList(NationListPayload payload, IPayloadContext context) {
        Map<UUID, NationSummary> byId = new LinkedHashMap<>();
        payload.nations().forEach(n -> byId.put(n.id(), n));
        nations = Map.copyOf(byId);
    }

    public static void onPlayerNation(PlayerNationPayload payload, IPayloadContext context) {
        ownNation = payload.nation().orElse(null);
        ownRole = payload.role().orElse(null);
    }

    public static void clear() {
        nations = Map.of();
        ownNation = null;
        ownRole = null;
    }
}
