package com.telco.webbff.gateway;

import java.util.UUID;

/**
 * Partial view of subscription-service's subscription read model, deserialized from the gateway
 * response ({@code /api/v1/subscriptions?customerId=...}). Only the fields web-bff needs to shape the
 * home and account views (and to look up per-subscription usage) are declared; unknown properties are
 * ignored (Spring Boot disables {@code FAIL_ON_UNKNOWN_PROPERTIES}). Local DTO on purpose: web-bff
 * must not depend on a domain service's types (no cross-service coupling, ADR-022).
 */
public record GatewaySubscription(
        UUID id,
        UUID customerId,
        String msisdn,
        String tariffCode,
        String status) {
}
