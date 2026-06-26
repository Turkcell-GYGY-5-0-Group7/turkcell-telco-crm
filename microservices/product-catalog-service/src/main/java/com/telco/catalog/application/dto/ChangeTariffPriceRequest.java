package com.telco.catalog.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** HTTP request body for changing a tariff's monthly fee (PATCH /api/v1/tariffs/{code}/price). */
public record ChangeTariffPriceRequest(
        @NotNull @DecimalMin("0.00")
        BigDecimal monthlyFee
) {
}
