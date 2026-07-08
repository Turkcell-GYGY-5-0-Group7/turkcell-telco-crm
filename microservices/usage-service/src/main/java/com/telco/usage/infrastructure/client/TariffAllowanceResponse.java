package com.telco.usage.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight DTO for the fields we need from the tokenless
 * {@code GET /api/v1/tariffs/{code}/allowance-snapshot} on product-catalog-service (see
 * {@link ProductCatalogClient} javadoc for why this endpoint, not the authenticated
 * {@code GET /api/v1/tariffs/{code}}, is used). {@code ignoreUnknown} tolerates the response's own
 * {@code code} field, which this client does not need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TariffAllowanceResponse(
        int minutesIncluded,
        int smsIncluded,
        int dataMbIncluded
) {
}
