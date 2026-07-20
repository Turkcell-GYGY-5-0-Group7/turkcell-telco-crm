package com.telco.usage.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Resets a subscription's current-period quota to a new tariff's allowances after a plan change
 * (Sprint 24 Feature 24.4, design-note D4). Dispatched by the
 * {@code subscription.tariff-changed.v1} consumer.
 *
 * <p>Idempotency: an {@link IdempotentRequest} keyed by the BUSINESS key
 * {@code "reprovision-quota:" + orderId} - one PLAN_CHANGE order re-provisions exactly once. The
 * Kafka record key is deliberately NOT used: tariff-changed events are keyed by subscriptionId
 * (the outbox aggregate_id), so a second plan change for the same subscription would collide with
 * the first and be swallowed as a duplicate.
 */
public record ReprovisionQuotaCommand(

        @NotNull
        UUID subscriptionId,

        @NotBlank
        String newTariffCode,

        /** Instant the tariff change was applied; selects the billing period being reset. */
        @NotNull
        Instant changedAt,

        /** The PLAN_CHANGE order id - the unique-per-event inbox dedup key. */
        @NotBlank
        String orderId

) implements Command<Void>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return "reprovision-quota:" + orderId;
    }
}
