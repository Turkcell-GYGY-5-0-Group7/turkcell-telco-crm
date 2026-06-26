package com.telco.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HTTP request body for refund (ADMIN only, POST /api/v1/payments/{paymentId}/refund). */
public record RefundPaymentRequest(

        @NotBlank @Size(max = 500)
        String reason
) {
}
