package com.telco.platform.starter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates and parses platform JWTs into claims, exposing the user id and roles.
 *
 * <p>Verification uses an HMAC secret when configured, otherwise an RSA public key. The optional
 * {@link #issue} helper signs tokens with the HMAC secret for tests and identity-issuing services.</p>
 */
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties.Jwt jwt;
    private final SecretKey hmacKey;
    private final PublicKey publicKey;
    private final JwtParser parser;

    public JwtService(JwtProperties.Jwt jwt) {
        this.jwt = jwt;
        this.hmacKey = buildHmacKey(jwt.getSecret());
        this.publicKey = buildPublicKey(jwt.getPublicKey());
        if (hmacKey == null && publicKey == null) {
            throw new IllegalStateException(
                    "telco.platform.security.jwt requires either a 'secret' (HMAC) or a 'public-key' (RSA)");
        }
        var builder = Jwts.parser();
        if (hmacKey != null) {
            builder.verifyWith(hmacKey);
        } else {
            builder.verifyWith(publicKey);
        }
        if (jwt.getIssuer() != null && !jwt.getIssuer().isBlank()) {
            builder.requireIssuer(jwt.getIssuer());
        }
        this.parser = builder.build();
    }

    /** Parses and verifies the token, returning its claims. Throws on invalid/expired tokens. */
    public Claims parse(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    /** Whether the token verifies and is not expired. Never throws. */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Extracts the user id (JWT subject) from the token. */
    public String userId(String token) {
        return parse(token).getSubject();
    }

    /** Extracts the roles claim as a set; empty when absent. */
    public Set<String> roles(String token) {
        return roles(parse(token));
    }

    /** Extracts the roles claim from already-parsed claims; empty when absent. */
    public Set<String> roles(Claims claims) {
        Object raw = claims.get(ROLES_CLAIM);
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (raw instanceof String s && !s.isBlank()) {
            return java.util.Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Collections.emptySet();
    }

    /**
     * Issues an HMAC-signed token for the given subject and roles. Requires the HMAC secret.
     * Intended for tests and identity services.
     */
    public String issue(String subject, Set<String> roles) {
        if (hmacKey == null) {
            throw new IllegalStateException("issue() requires an HMAC secret (telco.platform.security.jwt.secret)");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwt.getIssuer())
                .subject(subject)
                .claim(ROLES_CLAIM, roles == null ? List.of() : List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwt.getExpirySeconds())))
                .signWith(hmacKey)
                .compact();
    }

    private static SecretKey buildHmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private static PublicKey buildPublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String normalized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key for telco.platform.security.jwt.public-key", e);
        }
    }
}
