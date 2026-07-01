package com.telco.usage.application.command;

import com.telco.usage.application.dto.UsageAggregateResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregates overage usage for a subscription's billing period and emits
 * {@code usage.aggregated.v1} via the outbox for downstream billing processing.
 */
public record AggregateUsageCommand(

        @NotNull
        UUID subscriptionId,

        @NotNull
        Instant periodStart,

        @NotNull
        Instant periodEnd

) implements Command<UsageAggregateResponse> {
}
