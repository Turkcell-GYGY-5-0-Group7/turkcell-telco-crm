package com.telco.subscription.application.dto;

import com.telco.subscription.domain.Subscription;

import java.util.UUID;

/**
 * Internal, system-to-system read DTO for a subscription ({@code GET /internal/subscriptions/{id}},
 * Sprint 24 Feature 24.2). Consumed by order-service to validate the target subscription of ADDON
 * and PLAN_CHANGE orders (exists, ACTIVE, owned by the ordering customer, current tariff). Carries
 * only what that validation needs - deliberately leaner than the guarded
 * {@link SubscriptionResponse}.
 */
public record SubscriptionInternalResponse(
        UUID id,
        UUID customerId,
        String status,
        String tariffCode,
        int tariffVersion,
        String msisdn
) {

    public static SubscriptionInternalResponse from(Subscription subscription) {
        return new SubscriptionInternalResponse(
                subscription.getId(),
                subscription.getCustomerId(),
                subscription.getStatus().name(),
                subscription.getTariffCode(),
                subscription.getTariffVersion(),
                subscription.getMsisdn()
        );
    }
}
