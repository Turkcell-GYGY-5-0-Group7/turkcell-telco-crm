package com.telco.usage.application.command;

import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Provisions an initial quota for a newly activated subscription.
 *
 * <p>This command is a placeholder for Sprint 09 integration (feature 10.2.1). Full
 * implementation requires querying product-catalog for tariff allowances. The handler
 * currently logs a warning and returns null.
 */
public record ProvisionQuotaCommand(

        @NotNull
        UUID subscriptionId,

        @NotNull
        UUID customerId,

        @NotBlank
        String tariffCode,

        @NotNull
        Instant activatedAt

) implements Command<Void> {
}
