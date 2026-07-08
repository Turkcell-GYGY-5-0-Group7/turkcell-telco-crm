package com.telco.acceptance.support;

import java.time.Duration;

/**
 * Central, environment-driven configuration for the acceptance suite (task 14.1.1).
 *
 * <p>Every value has a localhost default so the suite runs unmodified both against a locally
 * booted {@code docker compose --profile auth --profile apps up} stack and in CI, where the
 * companion acceptance workflow exports the same variable names against the CI-side compose
 * network. Nothing in this class talks to the network; it only resolves configuration.
 */
public final class AcceptanceConfig {

    /** Gateway edge (ADR-011, ADR-015): every domain HTTP call in this suite goes through here. */
    public static final String GATEWAY_BASE_URL =
            env("GATEWAY_BASE_URL", "http://localhost:8080");

    /** Keycloak realm token endpoint (ADR-011); the gateway validates, never issues, tokens. */
    public static final String KEYCLOAK_TOKEN_URI =
            env("KEYCLOAK_TOKEN_URI", "http://localhost:8085/realms/telco-crm/protocol/openid-connect/token");

    /** Public client (no secret) with directAccessGrantsEnabled, per infra/docker/keycloak realm export. */
    public static final String KEYCLOAK_CLIENT_ID =
            env("KEYCLOAK_CLIENT_ID", "telco-web");

    /**
     * Seeded ADMIN-role realm user (infra/docker/keycloak/realm/realm-export.json). Reserved for
     * genuinely admin-only actions (KYC approval, tariff management, the bill-run trigger, and any
     * read gated by an ownership check this suite cannot satisfy as a real subscriber - see
     * {@link #KEYCLOAK_SUBSCRIBER_USERNAME} javadoc for that gap).
     */
    public static final String KEYCLOAK_ADMIN_USERNAME =
            env("KEYCLOAK_ADMIN_USERNAME", "admin@telco.local");

    public static final String KEYCLOAK_ADMIN_PASSWORD =
            env("KEYCLOAK_ADMIN_PASSWORD", "admin");

    /**
     * Seeded SUBSCRIBER-role realm user (infra/docker/keycloak/realm/realm-export.json, tied to
     * persona P1 "Subscriber (End-User)" per docs/architecture/keycloak-and-auth.md and
     * docs/product/personas.md).
     *
     * <p><b>Not usable for real ownership proofs (Section 14.1.1 ruling):</b> this account is
     * imported directly by the realm export, never provisioned through identity-service's
     * {@code POST /api/v1/users}, so it has no local identity-service {@code users} row and can
     * never be linked to a customer (the linkage consumer requires that row - see
     * {@code LinkCustomerToUserCommandHandler}). It remains useful for scenarios that genuinely do
     * not depend on ownership (placing/viewing an order the same subscriber created, viewing a
     * payment by order). Tests that need to prove "view my own resource" ownership
     * ({@code GET /api/v1/subscriptions*}, {@code GET /api/v1/invoices*},
     * {@code GET /api/v1/usage/subscriptions/{id}/quota|history},
     * {@code GET /api/v1/notifications/users/{userId}/history}) use
     * {@link com.telco.acceptance.support.SelfServiceSubscriber} instead, which provisions a fresh,
     * linkable account (Feature 14.4 closes the identity-to-customer linkage gap this javadoc used
     * to document as unresolved).
     */
    public static final String KEYCLOAK_SUBSCRIBER_USERNAME =
            env("KEYCLOAK_SUBSCRIBER_USERNAME", "subscriber@telco.local");

    public static final String KEYCLOAK_SUBSCRIBER_PASSWORD =
            env("KEYCLOAK_SUBSCRIBER_PASSWORD", "subscriber");

    /**
     * Kafka bootstrap servers, HOST listener (infra/docker/compose.yml: {@code KAFKA_HOST_PORT},
     * default 29092). Used only by AC-03 to produce {@code cdr.events}: usage-service has no public
     * HTTP endpoint to record a CDR (ingestion is event-only), and the existing CDR simulator lives
     * in usage-service's own test tree (not reusable from a standalone module), so this suite
     * produces the same wire payload directly with a raw Kafka client.
     */
    public static final String KAFKA_BOOTSTRAP_SERVERS =
            env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");

    /** Upper bound while polling for saga/event propagation across services. */
    public static final Duration SAGA_TIMEOUT = Duration.ofSeconds(
            Long.parseLong(env("ACCEPTANCE_SAGA_TIMEOUT_SECONDS", "60")));

    /** Poll interval used with the timeout above. */
    public static final Duration POLL_INTERVAL = Duration.ofMillis(
            Long.parseLong(env("ACCEPTANCE_POLL_INTERVAL_MILLIS", "500")));

    private AcceptanceConfig() {
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
