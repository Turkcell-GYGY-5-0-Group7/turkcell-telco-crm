package com.telco.payment.application.dto;

import com.telco.payment.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read DTO for a payment. Domain entities are never exposed directly (ADR-015). */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String status,
        String paymentRequestId,
        Instant createdAt,
        Instant updatedAt,
        int attemptCount
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaymentRequestId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getAttempts().size()
        );
    }
}
