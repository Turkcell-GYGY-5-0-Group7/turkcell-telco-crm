package com.telco.order.infrastructure.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight DTO for the product-catalog-service GET /internal/addons/{code} response body.
 * Only the fields needed to validate and price an ADDON order (FR-09) are mapped.
 */
public record AddonClientResponse(
        UUID id,
        String code,
        String name,
        BigDecimal price,
        String currency,
        String type
) {
}
