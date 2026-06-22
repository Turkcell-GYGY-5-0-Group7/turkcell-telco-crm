package com.telco.platform.common.context;

import java.util.Set;

/**
 * Immutable view of the authenticated principal for the current request.
 *
 * @param userId   stable user identifier; null for anonymous
 * @param roles    granted roles; never null
 * @param tenantId owning tenant; null when not tenant-scoped
 */
public record UserContext(String userId, Set<String> roles, String tenantId) {

    private static final UserContext ANONYMOUS = new UserContext(null, Set.of(), null);

    public UserContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Returns the shared anonymous (unauthenticated) context. */
    public static UserContext anonymous() {
        return ANONYMOUS;
    }

    /** Whether this principal holds the given role. */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
