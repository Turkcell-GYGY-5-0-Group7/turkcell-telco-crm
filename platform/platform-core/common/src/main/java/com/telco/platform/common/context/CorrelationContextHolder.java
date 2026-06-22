package com.telco.platform.common.context;

import java.util.Optional;

/**
 * Thread-local holder for the current {@link CorrelationContext}. Populated by starter-observability.
 */
public final class CorrelationContextHolder {

    private static final ThreadLocal<CorrelationContext> HOLDER = new ThreadLocal<>();

    private CorrelationContextHolder() {
    }

    /** Binds the correlation context to the current thread. */
    public static void set(CorrelationContext context) {
        HOLDER.set(context);
    }

    /** Returns the current correlation context if one is bound. */
    public static Optional<CorrelationContext> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    /** Removes any bound correlation context from the current thread. */
    public static void clear() {
        HOLDER.remove();
    }
}
