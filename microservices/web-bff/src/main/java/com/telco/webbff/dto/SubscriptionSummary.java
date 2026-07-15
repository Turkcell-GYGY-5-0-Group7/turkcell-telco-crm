package com.telco.webbff.dto;

/**
 * UI-shaped view of one subscription, composed from subscription-service. Carries the fields the
 * subscription read model exposes: id, MSISDN, tariff code and lifecycle status. The tariff display
 * name is not resolved here - it lives in product-catalog-service and is out of scope for the
 * single-fan-out account/home composition (16.5.1); the UI shows the tariff code.
 */
public record SubscriptionSummary(
        String subscriptionId,
        String msisdn,
        String tariffCode,
        String status) {
}
