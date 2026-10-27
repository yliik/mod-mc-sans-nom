package com.geopoliticsmod.territory;

import com.geopoliticsmod.core.Lang;
import com.geopoliticsmod.core.config.GeoConfig;
import com.geopoliticsmod.nation.Nation;
import com.geopoliticsmod.nation.NationSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Détection des passages de frontière, lancée par l'ordonnanceur toutes les
 * {@code borderCheckInterval} ticks. Pour chaque joueur on compare seulement son chunk au
 * précédent : la recherche du propriétaire n'a lieu qu'au changement de chunk.
 *
 * <p>Pour éviter un titre à chaque pas le long d'une frontière, un second passage dans les
 * {@link #TITLE_COOLDOWN_MS} s'affiche en barre d'action.
 */
public final class BorderTracker {
    private static final long TITLE_COOLDOWN_MS = 3000;

    private record Position(ResourceKey<Level> dimension, long chunk, UUID owner, long lastTitleMs) {}

    private static final Map<UUID, Position> LAST_POSITIONS = new HashMap<>();

    private BorderTracker() {}

    public static void tick(MinecraftServer server) {
        TerritorySavedData territory = TerritorySavedData.get(server);
        NationSavedData nations = NationSavedData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dimension = player.level().dimension();
            long chunk = player.chunkPosition().toLong();
            Position last = LAST_POSITIONS.get(player.getUUID());
            if (last != null && last.chunk() == chunk && last.dimension().equals(dimension)) continue;

            UUID owner = territory.ownerAt(dimension, chunk);
            long lastTitleMs = last == null ? 0 : last.lastTitleMs();
            // Pas d'annonce à la connexion : seulement quand le propriétaire change.
            if (last != null && !Objects.equals(owner, last.owner()) && GeoConfig.BORDER_TITLES.get()) {
                lastTitleMs = announce(player, nationOf(nations, last.owner()), nationOf(nations, owner), lastTitleMs);
            }
            LAST_POSITIONS.put(player.getUUID(), new Position(dimension, chunk, owner, lastTitleMs));
        }
    }

    private static Optional<Nation> nationOf(NationSavedData nations, UUID id) {
        return id == null ? Optional.empty() : nations.get(id);
    }

    /** Affiche l'entrée dans {@code entered} ou la sortie de {@code left}. Renvoie l'heure du dernier titre. */
    private static long announce(ServerPlayer player, Optional<Nation> left, Optional<Nation> entered, long lastTitleMs) {
        Component title;
        Component subtitle;
        if (entered.isPresent()) {
            Nation nation = entered.get();
            title = nation.displayName();
            subtitle = nation.motto().isEmpty()
                    ? Lang.tr("border.territory").withStyle(ChatFormatting.GRAY)
                    : Component.literal(nation.motto()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        } else if (left.isPresent()) {
            title = Lang.tr("border.wilderness").withStyle(ChatFormatting.DARK_GREEN);
            subtitle = Lang.tr("border.leaving", left.get().displayName()).withStyle(ChatFormatting.GRAY);
        } else {
            return lastTitleMs;
        }

        long now = System.currentTimeMillis();
        if (now - lastTitleMs < TITLE_COOLDOWN_MS) {
            player.displayClientMessage(Component.empty().append(title).append(" — ").append(subtitle), true);
            return lastTitleMs;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        return now;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POSITIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_POSITIONS.clear();
    }
}
