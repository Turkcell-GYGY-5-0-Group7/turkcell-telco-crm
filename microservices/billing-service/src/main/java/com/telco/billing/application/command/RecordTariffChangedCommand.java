package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Updates the billing read model's tariff code after a plan change (Sprint 24 Feature 24.4,
 * design-note D2), so the next bill run charges the new tariff's monthly fee. Dispatched by the
 * {@code subscription.tariff-changed.v1} consumer.
 *
 * <p>Idempotency: an {@link IdempotentRequest} keyed by the BUSINESS key
 * {@code "billing-tariff-changed:" + orderId} - the Kafka record key is the subscriptionId
 * (outbox aggregate_id) and would collide across successive plan changes of the same subscription.
 */
public record RecordTariffChangedCommand(

        @NotNull
        UUID subscriptionId,

        @NotBlank
        String newTariffCode,

        /** The PLAN_CHANGE order id - the unique-per-event inbox dedup key. */
        @NotBlank
        String orderId

) implements Command<Void>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return "billing-tariff-changed:" + orderId;
    }
}
