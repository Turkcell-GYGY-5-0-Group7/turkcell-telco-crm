package com.telco.dispute.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Withdraws a dispute the customer no longer wishes to pursue. Publishes {@code dispute.withdrawn.v1}.
 *
 * <p>A non-admin caller may only withdraw their own dispute -
 * {@link com.telco.dispute.application.handler.WithdrawDisputeCommandHandler} rejects (403) otherwise.
 */
public record WithdrawDisputeCommand(

        @NotNull
        UUID disputeId,

        @NotBlank
        String withdrawnBy,

        String callerCustomerId,

        boolean callerIsAdmin

) implements Command<Unit> {
}
