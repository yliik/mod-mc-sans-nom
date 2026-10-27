package com.geopoliticsmod.core.scheduler;

import com.geopoliticsmod.GeoPoliticsMod;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ordonnanceur à ticks espacés : seul abonné du mod à {@link ServerTickEvent}.
 *
 * <p>Chaque système s'inscrit avec un intervalle (lu à chaque passage, donc modifiable à chaud
 * via la config). Les tâches reçoivent un décalage différent pour ne pas tomber sur le même tick.
 * Une tâche qui dépasse {@link #SLOW_TASK_MS} est signalée dans les logs : son calcul doit alors
 * passer par {@link GeoExecutor}.
 */
public final class GeoScheduler {
    private static final long SLOW_TASK_MS = 5;
    private static final int OFFSET_STEP = 7;

    private record Task(String name, IntSupplier interval, int offset, Consumer<MinecraftServer> action) {}

    private static final List<Task> TASKS = new CopyOnWriteArrayList<>();
    private static long ticks;

    private GeoScheduler() {}

    /** Inscrit une tâche exécutée sur le thread serveur toutes les {@code interval} ticks. */
    public static void register(String name, IntSupplier interval, Consumer<MinecraftServer> action) {
        TASKS.add(new Task(name, interval, TASKS.size() * OFFSET_STEP, action));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = ++ticks;
        for (Task task : TASKS) {
            int interval = Math.max(1, task.interval().getAsInt());
            if ((tick + task.offset()) % interval != 0) continue;

            long start = System.nanoTime();
            try {
                task.action().accept(event.getServer());
            } catch (RuntimeException e) {
                GeoPoliticsMod.LOGGER.error("Tâche planifiée « {} » en échec", task.name(), e);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMs > SLOW_TASK_MS) {
                GeoPoliticsMod.LOGGER.warn("Tâche planifiée « {} » : {} ms sur le thread serveur", task.name(), elapsedMs);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ticks = 0;
    }
}
