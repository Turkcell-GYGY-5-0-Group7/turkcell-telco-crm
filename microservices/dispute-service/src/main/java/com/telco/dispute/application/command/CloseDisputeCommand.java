package com.telco.dispute.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Closes a resolved or withdrawn dispute once its downstream action (credit/refund/no-op) is
 * confirmed. Publishes {@code dispute.closed.v1}.
 */
public record CloseDisputeCommand(

        @NotNull
        UUID disputeId,

        @NotBlank
        String closedBy

) implements Command<Unit> {
}
