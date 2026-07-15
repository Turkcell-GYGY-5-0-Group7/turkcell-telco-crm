package com.telco.webbff.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partial view of product-catalog-service's tariff read model, deserialized from the gateway
 * response ({@code /api/v1/tariffs}). Only the fields web-bff needs to shape the onboarding catalog
 * and to resolve a tariff code to its id for order placement are declared; unknown properties are
 * ignored (Spring Boot disables {@code FAIL_ON_UNKNOWN_PROPERTIES}). This is a local DTO on purpose:
 * web-bff must not depend on a domain service's types (no cross-service coupling).
 */
public record GatewayTariff(
        UUID id,
        String code,
        String name,
        String type,
        String status,
        BigDecimal monthlyFee,
        String currency,
        String targetSegment) {
}
