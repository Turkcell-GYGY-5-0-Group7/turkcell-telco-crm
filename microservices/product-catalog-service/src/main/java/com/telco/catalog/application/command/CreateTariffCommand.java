package com.telco.catalog.application.command;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.domain.model.TariffType;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Creates a new tariff in the catalog (FR-CAT-01). */
public record CreateTariffCommand(

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

) implements Command<TariffResponse> {
}
