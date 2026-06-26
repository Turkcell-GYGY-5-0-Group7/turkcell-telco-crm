package com.telco.payment.application.query;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.platform.cqrs.Query;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Retrieves the payment for a given order. */
public record GetPaymentByOrderQuery(@NotNull UUID orderId) implements Query<PaymentResponse> {
}
