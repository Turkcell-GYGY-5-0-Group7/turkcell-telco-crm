package com.telco.order.application.dto;

import com.telco.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read DTO for a single order line item. Domain entities are never exposed directly (ADR-015).
 *
 * <p>{@code itemType} discriminates TARIFF vs ADDON lines (Sprint 24 Feature 24.2, design-note D1):
 * the tariff snapshot fields ({@code tariffId}/{@code tariffCode}/{@code tariffVersion}) are
 * {@code null} on ADDON items, while {@code productCode} is {@code null} on TARIFF items.
 * {@code tariffName} doubles as the generic product-name snapshot (addon name for ADDON items).
 * {@code targetSubscriptionId} is populated on standalone ADDON items and on a PLAN_CHANGE order's
 * TARIFF item.
 *
 * <p>{@code campaignId}/{@code campaignCode} are {@code null} when the item was priced at the
 * undiscounted tariff rate (Feature 21.3.3, ADR-027 Decision Section 4).
 */
public record OrderItemResponse(
        UUID id,
        String itemType,
        UUID tariffId,
        String tariffCode,
        Integer tariffVersion,
        String tariffName,
        String productCode,
        UUID targetSubscriptionId,
        BigDecimal unitPrice,
        int quantity,
        UUID campaignId,
        String campaignCode
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getItemType().name(),
                item.getTariffId(),
                item.getTariffCode(),
                item.getTariffVersion(),
                item.getTariffName(),
                item.getProductCode(),
                item.getTargetSubscriptionId(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getCampaignId(),
                item.getCampaignCode()
        );
    }
}
