package com.telco.order.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON payload shape for {@code subscription.activated.v1} consumed from the
 * {@code subscription.events} Kafka topic. Mirrors the subscription-service outbox payload
 * ({@code SubscriptionActivatedV1}); the {@code orderId} field is the saga correlation key. Unknown
 * fields are ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionActivatedPayload(
        String subscriptionId,
        String customerId,
        String msisdn,
        String tariffCode,
        Long activatedAt,
        String orderId
) {
}
