package com.telco.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keycloak Admin REST adapter (ADR-011). Provisions users and manages realm-role assignments through
 * the Keycloak Admin API. A fresh admin token is obtained per call using client-credentials grant;
 * no token caching is applied here (the Keycloak server handles token lifetime).
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
    public String createUser(String username, String email) {
        String token = fetchAdminToken();

        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
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
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
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

    @SuppressWarnings("unchecked")
    private String fetchAdminToken() {
        String formBody = "grant_type=client_credentials"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret;

        Map<String, Object> response = RestClient.create()
                .post()
                .uri(serverUrl + "/realms/master/protocol/openid-connect/token")
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
