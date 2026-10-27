package com.geopoliticsmod.core.scheduler;

import com.geopoliticsmod.GeoPoliticsMod;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;

/**
 * Calculs lourds hors du thread serveur (taux de change, décisions IA, scores).
 *
 * <p>Règle : on passe un <b>instantané immuable</b> pris sur le thread serveur, le calcul ne
 * touche jamais au monde ni aux SavedData, et le résultat est appliqué sur le thread serveur.
 * Un seul thread de travail : les calculs s'exécutent dans l'ordre de soumission.
 */
public final class GeoExecutor {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, GeoPoliticsMod.MODID + "-worker");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private GeoExecutor() {}

    /**
     * Calcule {@code computation(snapshot)} sur le thread de travail, puis appelle {@code apply}
     * avec le résultat sur le thread serveur. Les erreurs sont journalisées, {@code apply} n'est
     * alors pas appelé.
     */
    public static <S, R> CompletableFuture<R> compute(MinecraftServer server, S snapshot,
                                                      Function<S, R> computation, Consumer<R> apply) {
        return CompletableFuture.supplyAsync(() -> computation.apply(snapshot), WORKER)
                .whenCompleteAsync((result, error) -> {
                    if (error != null) {
                        GeoPoliticsMod.LOGGER.error("Calcul en arrière-plan en échec", error);
                    } else {
                        apply.accept(result);
                    }
                }, server);
    }
}
