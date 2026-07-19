package com.telco.payment.application.dto;

import com.telco.payment.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** HTTP request body for manual charge override (ADMIN only, POST /api/v1/payments). */
public record ChargePaymentRequest(

        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        /**
         * Idempotency key (back-compat). Optional since Sprint 24: the standard
         * {@code Idempotency-Key} header (PDF Section 12) wins when present; the controller
         * rejects the request when NEITHER the header nor this field is supplied.
         */
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
         * How the customer pays (FR-25). Optional: {@code null} defaults to
         * {@link PaymentMethod#CREDIT_CARD} in the handler. Label only in the MVP - the mock PSP
         * ignores the method (Sprint 24 design-note D6).
         */
        PaymentMethod method
) {
}
