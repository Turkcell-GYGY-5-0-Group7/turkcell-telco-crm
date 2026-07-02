package com.telco.identity.infrastructure;

import java.util.Set;

/**
 * Port to the Keycloak Admin API for user and realm-role provisioning (ADR-011).
 *
 * <p>Keycloak owns credentials, login, and token issuance; identity-service administers users and
 * role assignments through this Admin API and keeps a local projection for app-specific authorization
 * and audit. This interface is the framework-independent contract (ARC-02) the command handlers in
 * feature 5.5 depend on. Types crossing the boundary are plain identifiers and names - no Spring or
 * HTTP types leak through.
 *
 * <p>The concrete REST adapter (realm {@code telco-crm}, Keycloak Admin REST endpoints) is wired in
 * feature 5.5, where the provisioning APIs that invoke it are implemented and integration-tested
 * against a Keycloak instance. Feature 5.2 ships the contract only.
 */
public interface KeycloakAdminClient {

    /**
     * Provisions a user in the Keycloak realm and returns the Keycloak user id ({@code keycloakId})
     * used to link the local identity projection.
     */
    String createUser(String username, String email);

    /** Assigns the given realm roles to the Keycloak user. */
    void assignRealmRoles(String keycloakId, Set<String> roleNames);

    /** Removes the given realm roles from the Keycloak user. */
    void removeRealmRoles(String keycloakId, Set<String> roleNames);

    /** Disables the Keycloak user, preventing all logins. */
    void disableUser(String keycloakId);
}
