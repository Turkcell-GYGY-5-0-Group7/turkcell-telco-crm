package com.telco.order.application.dto;

import com.telco.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/** Read DTO for a single order line item. Domain entities are never exposed directly (ADR-015). */
public record OrderItemResponse(
        UUID id,
        UUID tariffId,
        String tariffCode,
        int tariffVersion,
        String tariffName,
        BigDecimal unitPrice,
        int quantity
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getTariffId(),
                item.getTariffCode(),
                item.getTariffVersion(),
                item.getTariffName(),
                item.getUnitPrice(),
                item.getQuantity()
        );
    }
}
