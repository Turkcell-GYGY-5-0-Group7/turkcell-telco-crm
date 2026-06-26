package com.telco.order.infrastructure.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight DTO for the product-catalog-service GET /api/v1/tariffs/{tariffId} response body.
 * Only the fields needed to validate and price the order are mapped.
 */
public record TariffClientResponse(
        UUID id,
        String code,
        String name,
        BigDecimal monthlyFee,
        String currency
) {
}
