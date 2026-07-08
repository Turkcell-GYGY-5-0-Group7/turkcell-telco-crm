package com.telco.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link KeycloakAdminRestClient} against a local {@link HttpServer} standing in for
 * the Keycloak Admin API, exercising the create/assign/remove/disable/link flows and their failure
 * paths (missing Location header, non-2xx responses, unresolved roles, a missing user
 * representation) without needing a live Keycloak instance.
 */
class KeycloakAdminRestClientTest {

    private static final String REALM = "telco-crm";
    private static final String CLIENT_ID = "telco-gateway";
    private static final String CLIENT_SECRET = "secret";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, HttpHandler> routes = new HashMap<>();
    private HttpServer server;
    private String serverUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            HttpHandler handler = routes.get(key);
            if (handler == null) {
                exchange.getRequestBody().readAllBytes();
                sendJson(exchange, 404, Map.of("error", "no route stubbed: " + key));
                return;
            }
            handler.handle(exchange);
        });
        server.start();
        serverUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private KeycloakAdminRestClient client() {
        return new KeycloakAdminRestClient(serverUrl, REALM, CLIENT_ID, CLIENT_SECRET);
    }

    private void routeToken(String accessToken) {
        routes.put("POST /realms/" + REALM + "/protocol/openid-connect/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            sendJson(exchange, 200, Map.of("access_token", accessToken, "token_type", "Bearer"));
        });
    }

    private void routeTokenMissingAccessToken() {
        routes.put("POST /realms/" + REALM + "/protocol/openid-connect/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            sendJson(exchange, 200, Map.of("token_type", "Bearer"));
        });
    }

    private static String bodyOf(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void sendEmptyWithLocation(HttpExchange exchange, int status, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    @Test
    void createUserProvisionsAccountAndSetsInitialPassword() throws IOException {
        routeToken("admin-token");
        AtomicReference<String> createBody = new AtomicReference<>();
        routes.put("POST /admin/realms/" + REALM + "/users", exchange -> {
            createBody.set(bodyOf(exchange));
            sendEmptyWithLocation(exchange, 201, serverUrl + "/admin/realms/" + REALM + "/users/kc-user-1");
        });
        AtomicReference<String> passwordBody = new AtomicReference<>();
        routes.put("PUT /admin/realms/" + REALM + "/users/kc-user-1/reset-password", exchange -> {
            passwordBody.set(bodyOf(exchange));
            sendEmpty(exchange, 204);
        });

        String keycloakId = client().createUser("jdoe", "jdoe@telco.local", "Jane", "Doe", "Secr3t!");

        assertThat(keycloakId).isEqualTo("kc-user-1");
        assertThat(createBody.get()).contains("\"username\":\"jdoe\"").contains("\"emailVerified\":true");
        assertThat(passwordBody.get()).contains("\"value\":\"Secr3t!\"").contains("\"temporary\":false");
    }

    @Test
    void createUserWithoutPasswordSkipsPasswordReset() {
        routeToken("admin-token");
        routes.put("POST /admin/realms/" + REALM + "/users", exchange ->
                sendEmptyWithLocation(exchange, 201, serverUrl + "/admin/realms/" + REALM + "/users/kc-user-2"));

        String keycloakId = client().createUser("asmith", "asmith@telco.local", "Ann", "Smith", null);

        assertThat(keycloakId).isEqualTo("kc-user-2");
        assertThat(routes).doesNotContainKey("PUT /admin/realms/" + REALM + "/users/kc-user-2/reset-password");
    }

    @Test
    void createUserWithoutLocationHeaderThrows() {
        routeToken("admin-token");
        routes.put("POST /admin/realms/" + REALM + "/users", exchange -> sendEmpty(exchange, 201));

        assertThatThrownBy(() -> client().createUser("x", "x@telco.local", "X", "Y", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no Location header");
    }

    @Test
    void createUserNonSuccessStatusThrows() {
        routeToken("admin-token");
        routes.put("POST /admin/realms/" + REALM + "/users", exchange ->
                sendJson(exchange, 409, Map.of("error", "exists")));

        assertThatThrownBy(() -> client().createUser("dup", "dup@telco.local", "D", "U", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Keycloak Admin API error");
    }

    @Test
    void fetchAdminTokenMissingAccessTokenThrows() {
        routeTokenMissingAccessToken();

        assertThatThrownBy(() -> client().createUser("x", "x@telco.local", "X", "Y", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no access_token");
    }

    @Test
    void assignRealmRolesResolvesRolesThenAssigns() {
        routeToken("admin-token");
        routes.put("GET /admin/realms/" + REALM + "/roles/SUBSCRIBER", exchange ->
                sendJson(exchange, 200, Map.of("id", "role-1", "name", "SUBSCRIBER")));
        AtomicReference<String> assignBody = new AtomicReference<>();
        routes.put("POST /admin/realms/" + REALM + "/users/kc-user-3/role-mappings/realm", exchange -> {
            assignBody.set(bodyOf(exchange));
            sendEmpty(exchange, 204);
        });

        client().assignRealmRoles("kc-user-3", Set.of("SUBSCRIBER"));

        assertThat(assignBody.get()).contains("\"name\":\"SUBSCRIBER\"");
    }

    @Test
    void assignRealmRolesUnknownRoleThrows() {
        routeToken("admin-token");
        routes.put("GET /admin/realms/" + REALM + "/roles/UNKNOWN", exchange ->
                sendJson(exchange, 404, Map.of("error", "not found")));

        assertThatThrownBy(() -> client().assignRealmRoles("kc-user-4", Set.of("UNKNOWN")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Keycloak Admin API error");
    }

    @Test
    void removeRealmRolesResolvesRolesThenRemoves() {
        routeToken("admin-token");
        routes.put("GET /admin/realms/" + REALM + "/roles/DEALER", exchange ->
                sendJson(exchange, 200, Map.of("id", "role-2", "name", "DEALER")));
        AtomicReference<String> removeBody = new AtomicReference<>();
        routes.put("DELETE /admin/realms/" + REALM + "/users/kc-user-5/role-mappings/realm", exchange -> {
            removeBody.set(bodyOf(exchange));
            sendEmpty(exchange, 204);
        });

        client().removeRealmRoles("kc-user-5", Set.of("DEALER"));

        assertThat(removeBody.get()).contains("\"name\":\"DEALER\"");
    }

    @Test
    void disableUserSendsEnabledFalse() {
        routeToken("admin-token");
        AtomicReference<String> disableBody = new AtomicReference<>();
        routes.put("PUT /admin/realms/" + REALM + "/users/kc-user-6", exchange -> {
            disableBody.set(bodyOf(exchange));
            sendEmpty(exchange, 204);
        });

        client().disableUser("kc-user-6");

        assertThat(disableBody.get()).contains("\"enabled\":false");
    }

    @Test
    void setCustomerIdAttributeMergesIntoExistingAttributesWithoutClearingProfile() {
        routeToken("admin-token");
        routes.put("GET /admin/realms/" + REALM + "/users/kc-user-7", exchange ->
                sendJson(exchange, 200, Map.of(
                        "id", "kc-user-7",
                        "username", "jdoe",
                        "email", "jdoe@telco.local",
                        "attributes", Map.of("locale", List.of("tr")))));
        AtomicReference<String> mergedBody = new AtomicReference<>();
        routes.put("PUT /admin/realms/" + REALM + "/users/kc-user-7", exchange -> {
            mergedBody.set(bodyOf(exchange));
            sendEmpty(exchange, 204);
        });

        UUID customerId = UUID.randomUUID();
        client().setCustomerIdAttribute("kc-user-7", customerId);

        assertThat(mergedBody.get())
                .contains("\"username\":\"jdoe\"")
                .contains("\"locale\":[\"tr\"]")
                .contains("\"customer_id\":[\"" + customerId + "\"]");
    }

    @Test
    void setCustomerIdAttributeWithNoExistingAttributesStillSucceeds() {
        routeToken("admin-token");
        routes.put("GET /admin/realms/" + REALM + "/users/kc-user-8", exchange ->
                sendJson(exchange, 200, Map.of("id", "kc-user-8", "username", "asmith")));
        AtomicReference<String> mergedBody = new AtomicReference<>();
        routes.put("PUT /admin/realms/" + REALM + "/users/kc-user-8", exchange -> {
            mergedBody.set(bodyOf(exchange));
            sendEmpty(exchange, 204);
        });

        UUID customerId = UUID.randomUUID();
        client().setCustomerIdAttribute("kc-user-8", customerId);

        assertThat(mergedBody.get()).contains("\"customer_id\":[\"" + customerId + "\"]");
    }

    @Test
    void setCustomerIdAttributeWithNoUserRepresentationThrows() {
        routeToken("admin-token");
        routes.put("GET /admin/realms/" + REALM + "/users/kc-user-9", exchange -> sendEmpty(exchange, 204));

        assertThatThrownBy(() -> client().setCustomerIdAttribute("kc-user-9", UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no user representation returned");
    }
}
