package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * Suspends a customer's ACTIVE subscriptions for non-payment, triggered by {@code payment.failed.v1}
 * (FR-14). Distinct from the id-targeted {@link SuspendSubscriptionCommand} because the payment event
 * correlates on {@code customerId}, not a subscription id.
 *
 * <p>Idempotent by two layers: (1) it is an {@link IdempotentRequest} keyed by the Kafka
 * {@code messageId} (the {@code payment.failed.v1} record key), so the mediator {@code InboxBehavior}
 * dedups a redelivered record INSIDE the handler transaction (ADR-005); and (2) the handler suspends
 * only subscriptions currently ACTIVE and skips those already SUSPENDED/TERMINATED, so even a record
 * with a different key is a no-op. Returns the number of subscriptions actually suspended.
 */
public record SuspendSubscriptionsForNonPaymentCommand(
        UUID customerId,
        String reason,
        String messageId
) implements Command<Integer>, IdempotentRequest {

    public SuspendSubscriptionsForNonPaymentCommand {
        Objects.requireNonNull(messageId, "messageId is required (idempotency key)");
    }

    @Override
    public String idempotencyKey() {
        return "suspend-non-payment:" + messageId;
    }
}
