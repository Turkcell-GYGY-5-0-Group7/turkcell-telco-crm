package com.telco.subscription.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight DTO for the order-service {@code GET /internal/orders/{orderId}} response body. Only
 * the fields the activation path needs are mapped; unknown fields are ignored for
 * forward-compatibility (ADR-019).
 *
 * <p>{@code customerId} from this response is authoritative for activation (the saga must not trust
 * the payment payload's customerId blindly). {@code items} carries the order's product snapshots;
 * since Sprint 24 Feature 24.2 an order may bundle ADDON items alongside its tariff, so the
 * activation invariant is "exactly one TARIFF item" (enforced by the consumer), no longer
 * {@code items.size() == 1}. {@code orderType} (NEW_LINE | ADDON | PLAN_CHANGE) is mapped so saga
 * consumers can branch on the persisted kind (design-note D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderClientResponse(
        UUID customerId,
        String orderType,
        String status,
        List<OrderItemClientResponse> items
) {
}
