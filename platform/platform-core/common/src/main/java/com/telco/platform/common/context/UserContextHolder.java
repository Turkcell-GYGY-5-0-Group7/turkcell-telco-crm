package com.telco.platform.common.context;

import java.util.Optional;

/**
 * Thread-local holder for the current {@link UserContext}. Pure JDK; populated by starter-security.
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /** Binds the user context to the current thread. */
    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    /** Returns the current user context if one is bound. */
    public static Optional<UserContext> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    /** Removes any bound user context from the current thread. */
    public static void clear() {
        HOLDER.remove();
    }
}
