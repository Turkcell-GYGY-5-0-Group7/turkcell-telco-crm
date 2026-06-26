package com.telco.payment.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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

        @NotBlank @Size(max = 64)
        String paymentRequestId
) {
}
