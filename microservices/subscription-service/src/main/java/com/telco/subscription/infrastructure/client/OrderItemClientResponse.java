package com.telco.subscription.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight DTO for a single line item in the order-service
 * {@code GET /internal/orders/{orderId}} response body. Only the fields the activation path needs
 * are mapped; unknown fields are ignored for forward-compatibility (ADR-019).
 *
 * <p>The tariff snapshot ({@code tariffCode} + {@code tariffVersion}) is taken from the order so the
 * subscription does exactly ONE synchronous hop and never reaches into product-catalog (architecture
 * Option (b): order-service snapshots the tariff and exposes it).
 *
 * <p>{@code itemType} (TARIFF | ADDON, Sprint 24 Feature 24.2) discriminates the tariff line to
 * activate from bundled addon lines; a {@code null} value (pre-24.2 order-service) means TARIFF.
 * For ADDON items {@code tariffCode} is {@code null} and {@code tariffVersion} deserializes to 0;
 * {@code productCode}/{@code targetSubscriptionId} are mapped for the addon fulfillment leg
 * (design-note D1) and are {@code null} on TARIFF items (except a PLAN_CHANGE order's tariff item,
 * which carries {@code targetSubscriptionId}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemClientResponse(
        String tariffCode,
        int tariffVersion,
        String itemType,
        String productCode,
        String targetSubscriptionId
) {

    /** Backward-compatible overload for pre-24.2 tariff-only items (tests and callers). */
    public OrderItemClientResponse(String tariffCode, int tariffVersion) {
        this(tariffCode, tariffVersion, "TARIFF", null, null);
    }

    /** True when this line is the tariff to activate; a {@code null} type means a pre-24.2 TARIFF item. */
    public boolean isTariffItem() {
        return itemType == null || "TARIFF".equals(itemType);
    }
}
