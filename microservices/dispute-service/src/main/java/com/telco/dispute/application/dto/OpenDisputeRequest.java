package com.telco.dispute.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenDisputeRequest(

        UUID invoiceId,

        UUID paymentId,

        @NotNull
        UUID customerId,

        @NotBlank
        String reasonCode,

        @NotNull @Positive
        BigDecimal disputedAmount

) {
}
