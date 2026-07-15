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

        @NotNull
        UUID tariffId,

        @Min(1)
        int quantity,

        String campaignCode

) {

    /** Backward-compatible overload for callers/tests that do not request a specific campaign. */
    public OrderItemRequest(UUID tariffId, int quantity) {
        this(tariffId, quantity, null);
    }
}
