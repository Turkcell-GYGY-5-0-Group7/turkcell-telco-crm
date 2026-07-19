package com.telco.webbff.dto;

import java.util.List;

/**
 * Dashboard composition for {@code GET /bff/v1/home}: the caller's profile, their active
 * subscriptions, and their latest invoice, shaped for the web home screen (web-bff contract).
 *
 * <p>This is a UI DTO returned directly, not wrapped in {@code ApiResult<T>}: the BFF is the
 * documented ADR-015 exception (see docs/api-contracts/web-bff.md Notes). The composition logic that
 * populates it lands in 16.5; 16.1.2 establishes the shape only.
 */
public record HomeResponse(
        ProfileSummary profile,
        List<SubscriptionSummary> activeSubscriptions,
        InvoiceSummary latestInvoice) {
}
