package com.telco.order.application.dto;

import com.telco.order.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read DTO for an order. Domain entities are never exposed directly (ADR-015).
 *
 * <p>{@code orderType} (NEW_LINE | ADDON | PLAN_CHANGE) is the kind derived from the items at
 * creation time (Sprint 24 Feature 24.2, design-note D1/D2).
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        String orderType,
        String status,
        String idempotencyKey,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getOrderType().name(),
                order.getStatus().name(),
                order.getIdempotencyKey(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
