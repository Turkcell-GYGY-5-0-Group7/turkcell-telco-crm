package com.telco.payment.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Reacts to {@code dispute.opened.v1} - marks the payment disputed, suppressing
 * {@code PaymentRetryScheduler}. No PSP call, no {@code PaymentStatus} transition (ADR-028 Section 5).
 */
public record MarkPaymentDisputedCommand(

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
