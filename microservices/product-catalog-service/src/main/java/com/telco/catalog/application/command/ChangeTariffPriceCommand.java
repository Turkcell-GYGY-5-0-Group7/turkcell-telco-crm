package com.telco.catalog.application.command;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Changes the monthly fee of an existing tariff and creates a new version snapshot (FR-CAT-03). */
public record ChangeTariffPriceCommand(

        @NotBlank
        String tariffCode,

        @NotNull @DecimalMin("0.00")
        BigDecimal newMonthlyFee

) implements Command<TariffResponse> {
}
