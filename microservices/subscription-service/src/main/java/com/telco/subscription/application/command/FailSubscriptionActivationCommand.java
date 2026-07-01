package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * Records a TERMINAL subscription-activation failure detected by the {@code payment.completed.v1}
 * consumer BEFORE an activation could be attempted - the order is genuinely missing (404), the order
 * lookup was rejected (non-404 4xx), or it violates the one-line-per-order MVP invariant. It emits
 * {@code subscription.activation-failed.v1} (keyed by {@code orderId}) so the saga compensates
 * (refund -> cancel), with no MSISDN allocated.
 *
 * <p>Distinct from the failure path inside {@link ActivateSubscriptionCommand}'s handler, which covers
 * failures discovered DURING activation (e.g. MSISDN pool exhausted). Both emit the same event shape.
 *
 * <p>Idempotency: this command is an {@link IdempotentRequest} keyed by the Kafka {@code messageId}
 * (the {@code payment.completed.v1} record key set by the outbox relay). The mediator
 * {@code InboxBehavior} dedups on it INSIDE the handler transaction (ADR-005), so a redelivered
 * terminal-failure record emits the compensation event at most once. This is a saga-only command, so
 * the message id is always available.
 */
public record FailSubscriptionActivationCommand(
        UUID orderId,
        UUID customerId,
        String reason,
        String messageId
) implements Command<Void>, IdempotentRequest {

    public FailSubscriptionActivationCommand {
        Objects.requireNonNull(orderId, "orderId is required (saga correlation key)");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(messageId, "messageId is required (idempotency key)");
    }

    @Override
    public String idempotencyKey() {
        return "fail-subscription-activation:" + messageId;
    }
}
