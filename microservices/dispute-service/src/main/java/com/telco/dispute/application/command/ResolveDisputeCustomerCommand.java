package com.telco.dispute.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resolves a dispute in the customer's favor - the ONLY command whose resulting event
 * ({@code dispute.resolved-customer.v1}) is a valid trigger for a real credit/refund downstream
 * (billing-service Feature 22.4 / payment-service Feature 22.5, ADR-028 Section 5).
 */
public record ResolveDisputeCustomerCommand(

        @NotNull
        UUID disputeId,

        @NotNull @Positive
        BigDecimal resolutionAmount,

        @NotBlank
        String resolvedBy

) implements Command<Unit> {
}
