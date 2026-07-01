package com.telco.usage.application.command;

import com.telco.usage.domain.UsageType;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a single CDR to the active quota for a subscription.
 * Idempotency is enforced by {@code cdrRef}: a second dispatch with the same reference
 * is detected via {@link com.telco.usage.infrastructure.persistence.UsageRecordRepository#existsByCdrRef}
 * and silently skipped.
 */
public record MeterCdrCommand(

        @NotNull
        UUID subscriptionId,

        @NotNull
        UsageType usageType,

        @Min(1)
        long quantity,

        @NotNull
        Instant occurredAt,

        @NotBlank @Size(max = 128)
        String cdrRef

) implements Command<Void> {
}
