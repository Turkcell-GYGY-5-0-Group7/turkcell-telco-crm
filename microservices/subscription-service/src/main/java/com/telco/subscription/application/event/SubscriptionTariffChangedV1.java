package com.telco.subscription.application.event;

/**
 * Versioned event payload published to the outbox as {@code subscription.tariff-changed.v1}
 * (Sprint 24 Feature 24.4, design-note D2; ADR-009, ADR-019). Emitted when a PLAN_CHANGE order
 * switches a subscription to a new tariff; keyed (aggregateId) by {@code subscriptionId}. Rides
 * the {@code subscription.events} topic as its third event type - consumers filter on the
 * {@code eventType} header. Payload matches the canonical {@code subscription-tariff-changed.avsc}
 * contract; {@code changedAt} is epoch milliseconds (Avro timestamp-millis).
 */
public record SubscriptionTariffChangedV1(
        String subscriptionId,
        String customerId,
        String msisdn,
        String previousTariffCode,
        String newTariffCode,
        int newTariffVersion,
        String orderId,
        long changedAt
) {
}
