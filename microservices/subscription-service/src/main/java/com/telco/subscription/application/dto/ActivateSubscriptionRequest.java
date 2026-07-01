package com.telco.subscription.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for the internal (saga-driven) activation endpoint {@code POST /api/v1/subscriptions}.
 *
 * <p>{@code orderId} is the saga correlation key carried from {@code payment.completed.v1}. It is
 * REQUIRED: on activation failure the handler emits {@code subscription.activation-failed.v1} keyed by
 * this {@code orderId} (a non-nullable field in the canonical Avro contract) so payment-service and
 * order-service can match the order to start compensation (9.4.3). Activation cannot proceed without it.
 */
public record ActivateSubscriptionRequest(
        @NotNull UUID orderId,
        @NotNull UUID customerId,
        @NotBlank String tariffCode,
        int tariffVersion
) {
}
