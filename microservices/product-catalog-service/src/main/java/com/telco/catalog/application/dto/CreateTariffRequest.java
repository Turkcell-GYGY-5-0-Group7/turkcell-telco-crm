package com.telco.catalog.application.dto;

import com.telco.catalog.domain.model.TariffType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** HTTP request body for creating a tariff (POST /api/v1/tariffs). */
public record CreateTariffRequest(

        @NotBlank @Size(max = 50)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        @NotNull
        TariffType type,

        @NotNull @DecimalMin("0.00")
        BigDecimal monthlyFee,

        @NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "[A-Z]{3}")
        String currency,

        @PositiveOrZero
        int minutesIncluded,

        @PositiveOrZero
        int smsIncluded,

        @PositiveOrZero
        int dataMbIncluded,

        @Size(max = 100)
        String targetSegment,

        @NotNull
        Instant effectiveFrom,

        Instant effectiveTo
) {
}
