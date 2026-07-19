package com.telco.order.application.dto;

import com.telco.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read DTO for a single order line item. Domain entities are never exposed directly (ADR-015).
 *
 * <p>{@code campaignId}/{@code campaignCode} are {@code null} when the item was priced at the
 * undiscounted tariff rate (Feature 21.3.3, ADR-027 Decision Section 4).
 */
public record OrderItemResponse(
        UUID id,
        UUID tariffId,
        String tariffCode,
        int tariffVersion,
        String tariffName,
        BigDecimal unitPrice,
        int quantity,
        UUID campaignId,
        String campaignCode
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getTariffId(),
                item.getTariffCode(),
                item.getTariffVersion(),
                item.getTariffName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getCampaignId(),
                item.getCampaignCode()
        );
    }
}
