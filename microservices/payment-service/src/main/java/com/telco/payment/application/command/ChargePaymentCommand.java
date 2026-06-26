package com.telco.payment.application.command;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Initiates a PSP charge for an order. Idempotency is enforced by
 * {@code paymentRequestId} in the handler: a second call with the same key returns
 * the existing payment without re-charging (FR-25, FR-26).
 */
public record ChargePaymentCommand(

        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        /**
         * Idempotency key, typically derived from {@code orderId}. A stable key ensures that
         * duplicate Kafka deliveries of {@code order.created.v1} do not double-charge the customer.
         */
        @NotBlank @Size(max = 64)
        String paymentRequestId

) implements Command<PaymentResponse> {
}
