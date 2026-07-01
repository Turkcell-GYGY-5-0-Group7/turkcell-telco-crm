package com.telco.usage.infrastructure.client;

/**
 * Lightweight DTO for the fields we need from
 * {@code GET /api/v1/tariffs/{code}} on product-catalog-service.
 */
public record TariffAllowanceResponse(
        int minutesIncluded,
        int smsIncluded,
        int dataMbIncluded
) {
}
