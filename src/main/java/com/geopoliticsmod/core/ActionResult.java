package com.geopoliticsmod.core;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Résultat d'une action métier : succès ou refus, avec le message à montrer à l'auteur
 * (absent quand l'action a déjà prévenu tout le monde).
 */
public record ActionResult(boolean success, Optional<Component> message) {
    public ActionResult(boolean success, Component message) {
        this(success, Optional.of(message));
    }

    public static ActionResult ok(String key, Object... args) {
        return new ActionResult(true, Lang.tr(key, args));
    }

    public static ActionResult fail(String key, Object... args) {
        return new ActionResult(false, Lang.tr(key, args));
    }

    public static ActionResult silent() {
        return new ActionResult(true, Optional.empty());
    }
}
