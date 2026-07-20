package com.telco.dispute.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Resolves a dispute in the merchant's favor - simply clears the hold, no financial change
 * (ADR-028 Section 5). Publishes {@code dispute.resolved-merchant.v1}.
 */
public record ResolveDisputeMerchantCommand(

        @NotNull
        UUID disputeId,

        @NotBlank
        String resolvedBy

) implements Command<Unit> {
}
