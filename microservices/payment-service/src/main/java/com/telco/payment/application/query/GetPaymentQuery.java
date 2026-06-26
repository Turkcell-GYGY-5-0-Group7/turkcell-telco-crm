package com.telco.payment.application.query;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.platform.cqrs.Query;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Retrieves a single payment by its internal ID. */
public record GetPaymentQuery(@NotNull UUID paymentId) implements Query<PaymentResponse> {
}
