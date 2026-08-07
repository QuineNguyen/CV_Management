package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.Role;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Manages thread-local context for the currently authenticated user (actor).
 *
 * <p>Design Principle: Every action or background task must be attributed to a real user account
 * to maintain a strict audit trail. A generic "SYSTEM" user is intentionally avoided.
 *
 * <p>Background jobs must propagate the initiating user's context using {@link #runAs(UUID, Role, Callable)}.
 */
public final class CurrentActor {
    public record Actor(UUID userId, Role role) {}

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    private CurrentActor() {}

    public static void set(UUID userId, Role role) {
        CURRENT.set(new Actor(userId, role));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<Actor> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID requireUserId() {
        return get().map(Actor::userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No current actor context found. Asynchronous or background tasks must use CurrentActor.runAs() "
                                + "with the initiating user's context."));
    }

    public static Role requireRole() {
        return get().map(Actor::role)
                .orElseThrow(() -> new IllegalStateException("No current actor role found in context."));
    }

    /**
     * Executes a unit of work under the context of a specified actor.
     * Restores the previous thread context upon completion to support nested executions.
     */
    public static <T> T runAs(UUID userId, Role role, Callable<T> work) throws Exception {
        Actor previous = CURRENT.get();
        set(userId, role);
        try {
            return work.call();
        } finally {
            if (previous == null) {
                clear();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}

