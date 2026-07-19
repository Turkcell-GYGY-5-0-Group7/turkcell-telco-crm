package com.telco.order.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for a single line item within a create-order request.
 *
 * <p>{@code campaignCode} is optional (Feature 21.3.3, ADR-027 Decision Section 4): when supplied,
 * {@code CampaignServiceClient.validate(...)} is asked to evaluate that specific campaign for this
 * item's tariff; when omitted, campaign-service auto-resolves the best-matching ACTIVE campaign for
 * the tariff (see {@code docs/api-contracts/campaign-service.md}). Either way, an ineligible or
 * unreachable-campaign-service outcome leaves the item priced at the undiscounted tariff rate.
 */
public record OrderItemRequest(

        /** Required on NEW_LINE/PLAN_CHANGE items; null on ADDON items (FR-09, handler-validated). */
        UUID tariffId,

        @Min(1)
        int quantity,

        String campaignCode,

        /** Catalog addon code; required on ADDON items, null otherwise (FR-09, handler-validated). */
        String addonCode

) {

    /** Backward-compatible overload for tariff items with no addon reference. */
    public OrderItemRequest(UUID tariffId, int quantity, String campaignCode) {
        this(tariffId, quantity, campaignCode, null);
    }

    /** Backward-compatible overload for callers/tests that do not request a specific campaign. */
    public OrderItemRequest(UUID tariffId, int quantity) {
        this(tariffId, quantity, null);
    }
}
