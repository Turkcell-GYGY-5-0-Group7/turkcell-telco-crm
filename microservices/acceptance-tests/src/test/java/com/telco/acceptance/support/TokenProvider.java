package com.telco.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches real Keycloak access tokens via the Resource Owner Password Credentials grant against
 * the public {@code telco-web} client (no secret needed, {@code directAccessGrantsEnabled=true}
 * per the realm export). This exercises the same OIDC token endpoint the gateway trusts (ADR-011),
 * so authorization here reflects the actual production trust chain rather than a self-signed
 * per-service test token.
 *
 * <p>Tokens are cached per username for the life of the JVM: the realm's access token lifespan is
 * 3600s, comfortably longer than one acceptance suite run.
 */
public final class TokenProvider {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private TokenProvider() {
    }

    /** Returns a cached or freshly fetched bearer token for the configured seeded ADMIN user. */
    public static String adminToken() {
        return tokenFor(AcceptanceConfig.KEYCLOAK_ADMIN_USERNAME, AcceptanceConfig.KEYCLOAK_ADMIN_PASSWORD);
    }

    /**
     * Returns a cached or freshly fetched bearer token for the configured seeded SUBSCRIBER user
     * (persona P1, see {@link AcceptanceConfig#KEYCLOAK_SUBSCRIBER_USERNAME} for the residual
     * ownership-linkage gap this token still cannot exercise).
     */
    public static String subscriberToken() {
        return tokenFor(AcceptanceConfig.KEYCLOAK_SUBSCRIBER_USERNAME, AcceptanceConfig.KEYCLOAK_SUBSCRIBER_PASSWORD);
    }

    /** Returns a cached or freshly fetched bearer token for an arbitrary seeded realm user. */
    public static String tokenFor(String username, String password) {
        return CACHE.computeIfAbsent(username, u -> fetchToken(u, password));
    }

    private static String fetchToken(String username, String password) {
        return RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "password")
                .formParam("client_id", AcceptanceConfig.KEYCLOAK_CLIENT_ID)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("scope", "openid")
                .post(AcceptanceConfig.KEYCLOAK_TOKEN_URI)
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
    }
}
