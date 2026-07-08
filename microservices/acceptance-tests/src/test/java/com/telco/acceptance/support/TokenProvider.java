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
     * (persona P1, {@link AcceptanceConfig#KEYCLOAK_SUBSCRIBER_USERNAME}). This account is imported
     * directly by the realm export, not provisioned through identity-service's
     * {@code POST /api/v1/users}, so it has no local identity-service {@code users} row and can
     * never be linked to a customer (Section 14.1.1 ruling) - it is only useful for scenarios that
     * do not need real customer ownership. Tests that need a genuine "view my own resource" proof
     * use {@link SelfServiceSubscriber} instead, which provisions a fresh, linkable account.
     */
    public static String subscriberToken() {
        return tokenFor(AcceptanceConfig.KEYCLOAK_SUBSCRIBER_USERNAME, AcceptanceConfig.KEYCLOAK_SUBSCRIBER_PASSWORD);
    }

    /** Returns a cached or freshly fetched bearer token for an arbitrary seeded realm user. */
    public static String tokenFor(String username, String password) {
        return CACHE.computeIfAbsent(username, u -> fetchToken(u, password));
    }

    /**
     * Always fetches a brand-new token, bypassing the cache. Required whenever a claim can change
     * between logins for the same user (Feature 14.4: the {@code customerId} claim only appears
     * once identity-to-customer linkage completes after the cached initial token was issued - see
     * {@link SelfServiceSubscriber}).
     */
    public static String freshTokenFor(String username, String password) {
        return fetchToken(username, password);
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
