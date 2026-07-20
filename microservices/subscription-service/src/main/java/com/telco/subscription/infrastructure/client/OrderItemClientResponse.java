package com.telco.subscription.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight DTO for a single line item in the order-service
 * {@code GET /api/v1/orders/{orderId}} response body. Only the tariff snapshot fields the activation
 * path needs are mapped; unknown fields are ignored for forward-compatibility (ADR-019).
 *
 * <p>The tariff snapshot ({@code tariffCode} + {@code tariffVersion}) is taken from the order so the
 * subscription does exactly ONE synchronous hop and never reaches into product-catalog (architecture
 * Option (b): order-service snapshots the tariff and exposes it).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemClientResponse(
        String tariffCode,
        int tariffVersion
) {
}
