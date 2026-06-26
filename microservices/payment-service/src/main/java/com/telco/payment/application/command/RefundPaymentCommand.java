package com.telco.payment.application.command;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Refunds a COMPLETED payment. Only COMPLETED -> REFUNDED is allowed (FR-27).
 */
public record RefundPaymentCommand(

        @NotNull
        UUID paymentId,

        @NotBlank @Size(max = 500)
        String reason

) implements Command<PaymentResponse> {
}
