package com.telco.identity.infrastructure;

import com.telco.platform.common.exception.DependencyFailureException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keycloak Admin REST adapter (ADR-011). Provisions users and manages realm-role assignments through
 * the Keycloak Admin API. A fresh admin token is obtained per call using client-credentials grant;
 * no token caching is applied here (the Keycloak server handles token lifetime).
 *
 * <p>Every outbound call is guarded by a {@code keycloak-admin} circuit breaker (Resilience4j).
 * Configuration is driven by the shared {@code application.yml} resilience4j block. When the
 * circuit is OPEN the fallback throws {@link DependencyFailureException} (HTTP 503 via the
 * platform GlobalExceptionHandler).
 */
@Component
public class KeycloakAdminRestClient implements KeycloakAdminClient {

    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminRestClient(
            @Value("${keycloak.admin.server-url}") String serverUrl,
            @Value("${keycloak.admin.realm}") String realm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "createUserFallback")
    public String createUser(String username, String email, String firstName, String lastName,
                              String password) {
        String token = fetchAdminToken();

        // firstName/lastName/emailVerified=true are mandatory, not cosmetic: the realm's declarative
        // Keycloak User Profile requires firstName/lastName/email for the account holder's own ("user")
        // context. A user created without them silently fails Keycloak's VERIFY_PROFILE required-action
        // trigger evaluation on its very first login attempt (added invisibly - it never shows up in a
        // GET .../users/{id} read of requiredActions taken beforehand), and the Resource Owner Password
        // Credentials grant can never resolve an interactive required action, so the account is
        // permanently stuck with invalid_grant/resolve_required_actions ("Account is not fully set up").
        // Confirmed live (Feature 14.4 end-to-end verification): patching a stuck account's
        // firstName/lastName/emailVerified immediately un-blocks its next login with no other change.
        // emailVerified is unconditionally true because this platform has no email-confirmation flow;
        // an admin-provisioned account's email is inherently already administrator-confirmed, exactly
        // matching every seeded realm-export.json user.
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "emailVerified", true,
                "enabled", true
        );

        URI location = RestClient.create()
                .post()
                .uri(serverUrl + "/admin/realms/" + realm + "/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .toBodilessEntity()
                .getHeaders()
                .getLocation();

        if (location == null) {
            throw new RuntimeException("Keycloak Admin API error: no Location header returned after user creation");
        }

        String path = location.getPath();
        String keycloakId = path.substring(path.lastIndexOf('/') + 1);

        if (password != null) {
            // A dedicated reset-password call, not an embedded `credentials` array on the create body -
            // both were verified equally effective once firstName/lastName/emailVerified were also
            // fixed, but the dedicated endpoint keeps this method's request body minimal and mirrors
            // the same admin operation a human operator would perform via the Keycloak console.
            setInitialPassword(token, keycloakId, password);
        }

        return keycloakId;
    }

    @SuppressWarnings("unchecked")
    private void setInitialPassword(String token, String keycloakId, String password) {
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );

        RestClient.create()
                .put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credential)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "assignRealmRolesFallback")
    public void assignRealmRoles(String keycloakId, Set<String> roleNames) {
        String token = fetchAdminToken();
        List<Map<String, Object>> roleRepresentations = resolveRoles(token, roleNames);

        RestClient.create()
                .post()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(roleRepresentations)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "removeRealmRolesFallback")
    public void removeRealmRoles(String keycloakId, Set<String> roleNames) {
        String token = fetchAdminToken();
        List<Map<String, Object>> roleRepresentations = resolveRoles(token, roleNames);

        RestClient.create()
                .method(HttpMethod.DELETE)
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(roleRepresentations)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "disableUserFallback")
    public void disableUser(String keycloakId) {
        String token = fetchAdminToken();
        RestClient.create()
                .put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", false))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = "keycloak-admin", fallbackMethod = "setCustomerIdAttributeFallback")
    @SuppressWarnings("unchecked")
    public void setCustomerIdAttribute(String keycloakId, UUID customerId) {
        String token = fetchAdminToken();

        // Keycloak's user PUT is a full-representation replace, not a partial patch: any top-level
        // field (username/email/firstName/lastName/attributes) omitted from the body is cleared on
        // the server, not left unchanged. Fetch the current representation first and merge only the
        // customer_id attribute into it, so this call can never silently wipe the user's existing
        // profile fields (bug found and fixed during Feature 14.4 end-to-end verification - confirmed
        // live: an earlier attributes-only PUT body cleared email/firstName/lastName on a real user).
        Map<String, Object> current = RestClient.create()
                .get()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .body(Map.class);

        if (current == null) {
            throw new RuntimeException(
                    "Keycloak Admin API error: no user representation returned for " + keycloakId);
        }

        Map<String, Object> mergedAttributes = new java.util.HashMap<>();
        Object existingAttributes = current.get("attributes");
        if (existingAttributes instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> mergedAttributes.put((String) k, v));
        }
        mergedAttributes.put("customer_id", List.of(customerId.toString()));

        Map<String, Object> merged = new java.util.HashMap<>(current);
        merged.put("attributes", mergedAttributes);

        RestClient.create()
                .put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(merged)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .toBodilessEntity();
    }

    // --- Fallback methods ---

    private String createUserFallback(String username, String email, String firstName, String lastName,
                                       String password, Throwable t) {
        throw new DependencyFailureException(
                "Keycloak Admin API unavailable: cannot create user " + username, t);
    }

    private void assignRealmRolesFallback(String keycloakId, Set<String> roleNames, Throwable t) {
        throw new DependencyFailureException(
                "Keycloak Admin API unavailable: cannot assign roles for " + keycloakId, t);
    }

    private void removeRealmRolesFallback(String keycloakId, Set<String> roleNames, Throwable t) {
        throw new DependencyFailureException(
                "Keycloak Admin API unavailable: cannot remove roles for " + keycloakId, t);
    }

    private void disableUserFallback(String keycloakId, Throwable t) {
        throw new DependencyFailureException(
                "Keycloak Admin API unavailable: cannot disable user " + keycloakId, t);
    }

    private void setCustomerIdAttributeFallback(String keycloakId, UUID customerId, Throwable t) {
        throw new DependencyFailureException(
                "Keycloak Admin API unavailable: cannot set customer_id attribute for " + keycloakId, t);
    }

    // --- Private helpers (called inside circuit-breaker-guarded public methods) ---

    @SuppressWarnings("unchecked")
    private String fetchAdminToken() {
        String formBody = "grant_type=client_credentials"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret;

        // The client-credentials grant must be requested from the realm the client is actually
        // registered in. keycloak.admin.client-id (telco-gateway) is a confidential client defined
        // in the target realm (see infra/docker/keycloak/realm/realm-export.json), not the Keycloak
        // "master" realm - posting to /realms/master previously always returned 401 invalid_client
        // against a real Keycloak server (confirmed live), which every KeycloakAdminClient call
        // (createUser, assignRealmRoles, removeRealmRoles, disableUser, setCustomerIdAttribute)
        // depends on. This had never been exercised against a live Keycloak container before Feature
        // 14.4's end-to-end verification - identity-service's own tests mock KeycloakAdminClient and
        // the acceptance suite only ever used pre-seeded realm users, never POST /api/v1/users.
        Map<String, Object> response = RestClient.create()
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        (req, res) -> {
                            throw new RuntimeException("Keycloak Admin API error: " + res.getStatusCode());
                        })
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Keycloak Admin API error: no access_token in token response");
        }

        return (String) response.get("access_token");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveRoles(String token, Set<String> roleNames) {
        return roleNames.stream()
                .map(roleName -> {
                    Map<String, Object> roleRep = RestClient.create()
                            .get()
                            .uri(serverUrl + "/admin/realms/" + realm + "/roles/" + roleName)
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .onStatus(status -> !status.is2xxSuccessful(),
                                    (req, res) -> {
                                        throw new RuntimeException(
                                                "Keycloak Admin API error: " + res.getStatusCode());
                                    })
                            .body(Map.class);

                    if (roleRep == null) {
                        throw new RuntimeException("Keycloak Admin API error: role not found: " + roleName);
                    }

                    return roleRep;
                })
                .collect(Collectors.toList());
    }
}
