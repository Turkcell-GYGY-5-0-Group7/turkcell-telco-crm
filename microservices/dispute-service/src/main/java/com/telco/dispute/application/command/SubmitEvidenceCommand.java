package com.telco.dispute.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Attaches an evidence object reference (uploaded via 22.3's MinIO flow) to a dispute under review
 * and publishes {@code dispute.evidence-submitted.v1}.
 *
 * <p>A non-admin caller may only submit evidence to their own dispute -
 * {@link com.telco.dispute.application.handler.SubmitEvidenceCommandHandler} rejects (403) otherwise.
 */
public record SubmitEvidenceCommand(

        @NotNull
        UUID disputeId,

        @NotBlank
        String submittedBy,

        @NotBlank
        String objectRef,

        String callerCustomerId,

        boolean callerIsAdmin

) implements Command<Unit> {
}
