package com.telco.subscription.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight DTO for the order-service {@code GET /api/v1/orders/{orderId}} response body. Only the
 * fields the activation path needs are mapped; unknown fields are ignored for forward-compatibility
 * (ADR-019).
 *
 * <p>{@code customerId} from this response is authoritative for activation (the saga must not trust
 * the payment payload's customerId blindly). {@code items} carries the order's tariff snapshot; the
 * tech-lead guardrail (one order = one line = one subscription) means exactly one item is expected -
 * the consumer enforces {@code items.size() == 1}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderClientResponse(
        UUID customerId,
        String status,
        List<OrderItemClientResponse> items
) {
}
