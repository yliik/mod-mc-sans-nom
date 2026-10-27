package com.geopoliticsmod.network;

import com.geopoliticsmod.client.ClientNationCache;
import com.geopoliticsmod.client.ClientTerritoryCache;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Enregistrement des paquets. Changer {@link #PROTOCOL} dès qu'un format de paquet change. */
public final class GeoNetwork {
    private static final String PROTOCOL = "2";

    private GeoNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        // Les caches client n'utilisent aucune classe client : on peut les référencer ici sans risque sur serveur dédié.
        registrar.playToClient(NationListPayload.TYPE, NationListPayload.STREAM_CODEC, ClientNationCache::onNationList);
        registrar.playToClient(PlayerNationPayload.TYPE, PlayerNationPayload.STREAM_CODEC, ClientNationCache::onPlayerNation);
        registrar.playToClient(TerritorySnapshotPayload.TYPE, TerritorySnapshotPayload.STREAM_CODEC, ClientTerritoryCache::onSnapshot);
        registrar.playToClient(ClaimDeltaPayload.TYPE, ClaimDeltaPayload.STREAM_CODEC, ClientTerritoryCache::onDelta);
    }
}
