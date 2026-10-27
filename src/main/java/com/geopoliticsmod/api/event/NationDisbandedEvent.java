package com.geopoliticsmod.api.event;

import com.geopoliticsmod.nation.Nation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Publié sur {@code NeoForge.EVENT_BUS} après la dissolution d'une nation (déjà retirée du
 * registre). Les modules qui gardent des données liées à la nation (territoire, économie…)
 * s'y abonnent pour les nettoyer.
 */
public class NationDisbandedEvent extends Event {
    private final MinecraftServer server;
    private final Nation nation;

    public NationDisbandedEvent(MinecraftServer server, Nation nation) {
        this.server = server;
        this.nation = nation;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Nation getNation() {
        return nation;
    }
}
