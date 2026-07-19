package com.telco.payment.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Reacts to {@code dispute.resolved-merchant.v1} - clears the disputed flag, no financial change
 * (ADR-028 Section 5).
 */
public record ClearPaymentDisputedCommand(

        @NotNull
        UUID paymentId,

        @NotBlank @Size(max = 255)
        String messageId

) implements Command<Unit>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
