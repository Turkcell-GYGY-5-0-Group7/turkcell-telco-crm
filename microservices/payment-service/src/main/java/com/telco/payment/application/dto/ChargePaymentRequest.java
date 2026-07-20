package com.telco.payment.application.dto;

import com.telco.payment.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * HTTP request body for manual charge override (ADMIN only, POST /api/v1/payments).
 *
 * <p>{@code paymentRequestId} is optional in the body: the {@code Idempotency-Key} header takes
 * precedence when present (FR-25, feature 24.6); the controller rejects the request with 400 if
 * neither is supplied.
 */
public record ChargePaymentRequest(

        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        @Size(max = 64)
        String paymentRequestId,

        /**
         * Invoice this charge settles, when paying a specific invoice (Section 14.2 pay-invoice
         * flow). Optional: omit for a plain order charge. When present, the resulting
         * {@code payment.completed.v1}/{@code payment.failed.v1} event carries it so
         * billing-service can mark the invoice paid.
         */
        UUID invoiceId,

        /**
         * Settlement method (FR-25): CREDIT_CARD, BANK_TRANSFER, or WALLET.
         * Optional - omitting it defaults to CREDIT_CARD.
         */
        PaymentMethod method
) {
}
