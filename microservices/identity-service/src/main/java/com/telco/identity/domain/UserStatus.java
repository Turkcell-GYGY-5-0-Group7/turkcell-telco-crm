package com.telco.identity.domain;

/**
 * Lifecycle state of a {@link User} in the identity projection.
 *
 * <p>A user starts {@link #PENDING} on provisioning, becomes {@link #ACTIVE} once enabled, and can be
 * {@link #LOCKED} to deny access. Credentials and the login flow live in Keycloak (ADR-011); this
 * status is the application-side view used for authorization decisions and audit.
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    LOCKED,
    DELETED
}
