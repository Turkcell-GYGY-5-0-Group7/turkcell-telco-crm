package com.telco.order.application.dto;

import com.telco.order.domain.model.OrderItemType;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/**
 * Request DTO for a single line item within a create-order request.
 *
 * <p>{@code itemType} is optional and defaults to {@link OrderItemType#TARIFF} when omitted, so
 * every pre-Sprint-24 request body keeps working unchanged (Feature 24.2, design-note D1).
 * {@code tariffId} is therefore no longer bean-mandatory: the {@code CreateOrderCommandHandler}
 * validation matrix enforces it for TARIFF items and enforces {@code productCode} for ADDON items,
 * since the requirement depends on the item's type. {@code targetSubscriptionId} points at an
 * existing subscription for standalone ADDON purchases and PLAN_CHANGE orders; it must be absent on
 * NEW_LINE orders.
 *
 * <p>{@code campaignCode} is optional and TARIFF-only (Feature 21.3.3, ADR-027 Decision Section 4):
 * when supplied, {@code CampaignServiceClient.validate(...)} is asked to evaluate that specific
 * campaign for this item's tariff; when omitted, campaign-service auto-resolves the best-matching
 * ACTIVE campaign for the tariff (see {@code docs/api-contracts/campaign-service.md}). Either way,
 * an ineligible or unreachable-campaign-service outcome leaves the item priced at the undiscounted
 * tariff rate. Supplying it on an ADDON item is a validation error (campaign eligibility is
 * tariff-scoped, ADR-027).
 */
public record OrderItemRequest(

        UUID tariffId,

        @Min(1)
        int quantity,

        String campaignCode,

        OrderItemType itemType,

        String productCode,

        UUID targetSubscriptionId

) {

    /** Backward-compatible overload for callers/tests that do not request a specific campaign. */
    public OrderItemRequest(UUID tariffId, int quantity) {
        this(tariffId, quantity, null);
    }

    /** Backward-compatible overload for pre-24.2 tariff-only items. */
    public OrderItemRequest(UUID tariffId, int quantity, String campaignCode) {
        this(tariffId, quantity, campaignCode, null, null, null);
    }

    /** The item's type with the omitted-field default applied: {@code TARIFF} when {@code null}. */
    public OrderItemType effectiveItemType() {
        return itemType == null ? OrderItemType.TARIFF : itemType;
    }
}
