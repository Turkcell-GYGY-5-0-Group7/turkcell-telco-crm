package com.telco.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON payload shape for the {@code subscription.activation-failed.v1} event consumed from the
 * {@code subscription.events} Kafka topic (saga compensation, Feature 9.4.3).
 *
 * <p>Fields mirror the {@code SubscriptionActivationFailedV1} Avro contract
 * ({@code platform-event-contracts}). The {@code orderId} is the saga correlation key
 * payment-service matches on to find the COMPLETED payment to refund. {@code subscriptionId} is
 * nullable when the failure occurred before a subscription id was assigned. Unknown fields are
 * ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionActivationFailedPayload(
        String orderId,
        String customerId,
        String subscriptionId,
        String reason,
        Object failedAt
) {
}
