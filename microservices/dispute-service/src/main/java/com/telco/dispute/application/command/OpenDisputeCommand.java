package com.telco.dispute.application.command;

import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Opens a new dispute and moves it straight to {@code UNDER_REVIEW}, publishing
 * {@code dispute.opened.v1}. At least one of {@code invoiceId}/{@code paymentId} must be set - the
 * domain guard ({@link com.telco.dispute.domain.Dispute#create}) enforces this, not this record.
 *
 * <p>A non-admin caller may only open a dispute for themselves -
 * {@link com.telco.dispute.application.handler.OpenDisputeCommandHandler} rejects (403) a
 * {@code customerId} that doesn't match {@code callerCustomerId}.
 */
public record OpenDisputeCommand(

        UUID invoiceId,

        UUID paymentId,

        @NotNull
        UUID customerId,

        @NotBlank
        String reasonCode,

        @NotNull @Positive
        BigDecimal disputedAmount,

        String callerCustomerId,

        boolean callerIsAdmin

) implements Command<UUID> {
}
