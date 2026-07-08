package com.telco.acceptance.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Minimal, dependency-free JWT payload decoder for assertions the acceptance suite needs to make
 * about claims (Feature 14.4: confirming a fresh token carries the resolved {@code customerId}
 * claim). Deliberately does not verify the signature - the suite already trusts the token because
 * it came directly from the real Keycloak token endpoint (ADR-011); this only reads the payload
 * back out for assertions, exactly like decoding a response body.
 */
public final class JwtClaims {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private JwtClaims() {
    }

    /** Returns the {@code customerId} claim, or {@code null} if absent/not yet linked. */
    public static String customerId(String token) {
        return claim(token, "customerId");
    }

    /** Returns the given top-level claim as a string, or {@code null} if absent or not a string. */
    public static String claim(String token, String claimName) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Not a JWT: expected at least 2 dot-separated segments");
        }
        byte[] payloadBytes = DECODER.decode(pad(parts[1]));
        try {
            JsonNode payload = MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode value = payload.get(claimName);
            return (value == null || value.isNull()) ? null : value.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode JWT payload", e);
        }
    }

    private static String pad(String base64Url) {
        int rem = base64Url.length() % 4;
        return rem == 0 ? base64Url : base64Url + "=".repeat(4 - rem);
    }
}
