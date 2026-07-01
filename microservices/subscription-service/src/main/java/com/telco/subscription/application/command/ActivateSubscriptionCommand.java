package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * Activates a new subscription for a customer: allocates a FREE MSISDN atomically and persists the
 * subscription in ACTIVE state, emitting {@code msisdn.allocated.v1} and {@code subscription.activated.v1}
 * on success (FR-13, FR-15).
 *
 * <p>The REST entry point for this command is delivered by feature 9.3; the saga wiring that triggers
 * activation from upstream order/payment events is delivered by feature 9.4.
 *
 * <p>{@code orderId} is the saga correlation key carried from {@code payment.completed.v1} and is
 * REQUIRED: on activation failure the handler emits {@code subscription.activation-failed.v1} keyed by
 * this {@code orderId} (a non-nullable field in the canonical Avro contract) so payment-service and
 * order-service can start compensation (9.4.3). The compact constructor rejects a null
 * {@code orderId}/{@code customerId} so a contract-invalid failure event can never be emitted.
 *
 * <p>Idempotency: this command is an {@link IdempotentRequest} keyed by {@code orderId}. One order
 * yields exactly one activation, so {@code orderId} is the natural business idempotency key shared by
 * BOTH entry points - the REST endpoint (which supplies it in the request body) and the
 * {@code payment.completed.v1} saga consumer (which carries it on the event). The mediator
 * {@code InboxBehavior} dedups on this key INSIDE the handler transaction (ADR-005), so a redelivered
 * {@code payment.completed.v1} - or a duplicate REST call for the same order - activates at most once.
 */
public record ActivateSubscriptionCommand(
        UUID orderId,
        UUID customerId,
        String tariffCode,
        int tariffVersion
) implements Command<UUID>, IdempotentRequest {

    public ActivateSubscriptionCommand {
        Objects.requireNonNull(orderId, "orderId is required (saga correlation key)");
        Objects.requireNonNull(customerId, "customerId is required");
    }

    @Override
    public String idempotencyKey() {
        return "activate-subscription:" + orderId;
    }
}
