package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * Switches an ACTIVE subscription to a new tariff as the PLAN_CHANGE leg of the order saga
 * (FR-09 package change, Sprint 24 Feature 24.4, design-note D2). Dispatched by the
 * {@code payment.completed.v1} consumer once the plan-change order is paid; on success the handler
 * emits {@code subscription.tariff-changed.v1}, on terminal failure it reuses the activation-failed
 * emitter so the existing refund/cancel compensation runs (documented reuse, D2).
 *
 * <p>{@code newTariffCode}/{@code newTariffVersion} are the order's catalog snapshot pinned at
 * order-creation time. {@code orderId} is the saga correlation key and is REQUIRED (the failure
 * event's aggregateId). {@code customerId} is the order's authoritative customer, re-validated
 * against the subscription's owner.
 *
 * <p>Idempotency: an {@link IdempotentRequest} keyed by {@code orderId} - one PLAN_CHANGE order
 * yields exactly one tariff change; the mediator {@code InboxBehavior} dedups on this key INSIDE
 * the handler transaction (ADR-005), so a redelivered {@code payment.completed.v1} changes the
 * tariff at most once.
 */
public record ChangeTariffCommand(
        UUID orderId,
        UUID customerId,
        UUID subscriptionId,
        String newTariffCode,
        int newTariffVersion
) implements Command<UUID>, IdempotentRequest {

    public ChangeTariffCommand {
        Objects.requireNonNull(orderId, "orderId is required (saga correlation key)");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(subscriptionId, "subscriptionId is required");
        Objects.requireNonNull(newTariffCode, "newTariffCode is required");
    }

    @Override
    public String idempotencyKey() {
        return "change-tariff:" + orderId;
    }
}
