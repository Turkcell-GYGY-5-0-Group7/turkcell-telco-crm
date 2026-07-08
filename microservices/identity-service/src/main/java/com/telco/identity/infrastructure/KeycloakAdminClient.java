package com.telco.identity.infrastructure;

import java.util.Set;
import java.util.UUID;

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
     * used to link the local identity projection. {@code firstName}/{@code lastName} are mandatory
     * and {@code emailVerified} is unconditionally set {@code true} (this platform has no
     * email-confirmation flow; an admin-provisioned account's email is inherently already
     * administrator-confirmed) - both are required to satisfy the realm's declarative Keycloak User
     * Profile, without which the account fails Keycloak's {@code VERIFY_PROFILE}/{@code VERIFY_EMAIL}
     * required-action checks on every login and can never authenticate via the Resource Owner Password
     * Credentials grant (Feature 14.4 root cause). {@code password} is optional (nullable); when
     * present, a non-temporary password credential is set at creation time so the account can
     * authenticate immediately. When {@code null}, the user is created with no credential at all
     * (pre-existing behavior).
     */
    String createUser(String username, String email, String firstName, String lastName, String password);

    /** Assigns the given realm roles to the Keycloak user. */
    void assignRealmRoles(String keycloakId, Set<String> roleNames);

    /** Removes the given realm roles from the Keycloak user. */
    void removeRealmRoles(String keycloakId, Set<String> roleNames);

    /** Disables the Keycloak user, preventing all logins. */
    void disableUser(String keycloakId);

    /**
     * Sets the {@code customer_id} user attribute on the Keycloak user so the {@code customerId}
     * protocol mapper can expose it as a JWT claim on the user's next login (Feature 14.4).
     */
    void setCustomerIdAttribute(String keycloakId, UUID customerId);
}
