package com.telco.webbff.gateway;

import java.util.UUID;

/**
 * Partial view of order-service's order read model, deserialized from the gateway response
 * ({@code POST /api/v1/orders}). web-bff surfaces the order id and saga status so the UI can poll
 * {@code GET /api/v1/orders/{id}} directly. Local DTO; no cross-service coupling.
 */
public record GatewayOrder(
        UUID id,
        String status) {
}
