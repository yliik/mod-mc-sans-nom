package com.geopoliticsmod.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration serveur (fichier par monde : serverconfig/geopoliticsmod-server.toml).
 *
 * <p>Les intervalles sont en ticks serveur (20 ticks = 1 s). Aucune logique lourde ne tourne
 * à chaque tick : chaque système s'inscrit dans l'ordonnanceur avec l'un de ces intervalles.
 */
public final class GeoConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("scheduler");
    }

    public static final ModConfigSpec.IntValue ECONOMY_INTERVAL = BUILDER
            .comment("Intervalle de mise à jour économique (inflation, taux de change, tributs), en ticks.")
            .defineInRange("economyInterval", 20 * 60 * 5, 20 * 10, 20 * 60 * 60);

    public static final ModConfigSpec.IntValue NPC_NATION_INTERVAL = BUILDER
            .comment("Intervalle de décision des nations PNJ (logique abstraite), en ticks.")
            .defineInRange("npcNationInterval", 20 * 60 * 2, 20 * 10, 20 * 60 * 60);

    public static final ModConfigSpec.IntValue POWER_INTERVAL = BUILDER
            .comment("Intervalle de recalcul du Power des nations, en ticks.")
            .defineInRange("powerInterval", 20 * 60, 20 * 5, 20 * 60 * 60);

    public static final ModConfigSpec.IntValue BORDER_CHECK_INTERVAL = BUILDER
            .comment("Intervalle de vérification des passages de frontière par joueur, en ticks.")
            .defineInRange("borderCheckInterval", 10, 1, 200);

    static {
        BUILDER.pop();
        BUILDER.push("territory");
    }

    public static final ModConfigSpec.IntValue BASE_CLAIMS = BUILDER
            .comment("Nombre de chunks qu'une nation peut revendiquer, quelle que soit sa taille.")
            .defineInRange("baseClaims", 16, 0, 100_000);

    public static final ModConfigSpec.IntValue CLAIMS_PER_MEMBER = BUILDER
            .comment("Chunks supplémentaires par membre de la nation.")
            .defineInRange("claimsPerMember", 8, 0, 100_000);

    public static final ModConfigSpec.BooleanValue REQUIRE_CONTIGUOUS_CLAIMS = BUILDER
            .comment("Un nouveau chunk doit toucher (par un côté) un chunk déjà revendiqué dans la même dimension.",
                    "Le premier chunk d'une nation dans une dimension peut être pris n'importe où.")
            .define("requireContiguousClaims", true);

    public static final ModConfigSpec.BooleanValue PROTECT_FROM_EXPLOSIONS = BUILDER
            .comment("Les explosions ne détruisent pas les blocs des territoires revendiqués.")
            .define("protectFromExplosions", true);

    public static final ModConfigSpec.BooleanValue ALLOW_FAKE_PLAYERS = BUILDER
            .comment("Autorise les machines qui agissent comme des joueurs (déployeurs Create, Mekanism…) dans les territoires.",
                    "À désactiver si des machines placées hors d'un territoire servent à le piller.")
            .define("allowFakePlayers", true);

    public static final ModConfigSpec.BooleanValue BORDER_TITLES = BUILDER
            .comment("Affiche un titre à l'écran quand un joueur entre dans une nation ou en sort.")
            .define("borderTitles", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SERVER_SPEC = BUILDER.build();

    private GeoConfig() {}
}
