package com.telco.catalog.application.dto;

import com.telco.catalog.domain.model.AddonType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

/**
 * HTTP request body for creating an addon (POST /api/v1/addons). Allowance fields are optional
 * and type-dependent; {@code applicableTariffCodes} links the addon to existing tariffs.
 */
public record CreateAddonRequest(

        @NotBlank @Size(max = 50)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        @NotNull @DecimalMin("0.00")
        BigDecimal price,

        @NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "[A-Z]{3}")
        String currency,

        @NotNull
        AddonType type,

        @Positive
        int validityDays,

        @PositiveOrZero
        Long dataMb,

        @PositiveOrZero
        Long voiceMinutes,

        @PositiveOrZero
        Long smsCount,

        Set<String> applicableTariffCodes
) {
}
