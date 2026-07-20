package com.telco.fraud.application.command;

import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Appends one raw {@link com.telco.fraud.domain.MsisdnLifecycleSignal} row for a consumed
 * subscription-service lifecycle event and then triggers the rule evaluators that key off that event
 * type (Feature 23.2.1). Dispatched by the four inbox-backed consumers, whose only job is to map the
 * Avro/JSON envelope onto this command (no business logic in the consumer).
 *
 * <p>{@code customerId}, {@code msisdn}, {@code subscriptionId}, and {@code reason} are nullable
 * because the observable fields vary by event type; {@code reason} is populated only for
 * {@code SUBSCRIPTION_SUSPENDED} (ADR-029 Amendment 3). For an {@code MSISDN_RELEASED} event that
 * arrived without a {@code customerId} (older producers, ADR-029 Amendment 1), the handler resolves it
 * defensively from the most recent prior allocation.
 */
public record IngestLifecycleSignalCommand(
        MsisdnLifecycleEventType eventType,
        UUID customerId,
        String msisdn,
        UUID subscriptionId,
        Instant occurredAt,
        String reason
) implements Command<Void> {
}
