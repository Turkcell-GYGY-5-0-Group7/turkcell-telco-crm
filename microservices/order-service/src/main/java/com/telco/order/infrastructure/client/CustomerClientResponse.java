package com.telco.order.infrastructure.client;

import java.util.UUID;

/**
 * Lightweight DTO for the customer-service GET /internal/customers/{customerId} response body.
 * Only the fields needed for order validation are mapped; Jackson ignores unknown fields.
 */
public record CustomerClientResponse(
        UUID id,
        String status
) {
}
