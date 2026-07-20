package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Applies a PLAN_CHANGE order to an existing subscription (FR-09), emitting
 * {@code subscription.tariff-changed.v1}. Dispatched by the {@code order.created.v1} consumer.
 *
 * <p>Idempotency: an {@link IdempotentRequest} keyed on the Kafka messageId; the platform
 * {@code InboxBehavior} dedups redelivery inside the handler transaction (ADR-005).
 */
public record ChangeSubscriptionTariffCommand(
        @NotNull
        UUID subscriptionId,

        @NotNull
        UUID orderId,

        /** Customer the order was placed for; must own the target subscription (guarded). */
        @NotNull
        UUID customerId,

        @NotBlank
        String newTariffCode,

        @NotBlank
        String messageId
) implements Command<UUID>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
