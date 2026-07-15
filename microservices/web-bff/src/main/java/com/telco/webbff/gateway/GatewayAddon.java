package com.telco.webbff.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partial view of product-catalog-service's addon read model, deserialized from the gateway response
 * ({@code /api/v1/addons?tariffCode=...}). Local DTO (no cross-service coupling); unused fields are
 * ignored on deserialization.
 */
public record GatewayAddon(
        UUID id,
        String code,
        String name,
        BigDecimal price,
        String currency) {
}
