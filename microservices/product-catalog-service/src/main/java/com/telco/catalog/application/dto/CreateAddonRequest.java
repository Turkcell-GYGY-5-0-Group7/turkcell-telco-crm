package com.telco.catalog.application.dto;

import com.telco.catalog.domain.model.AddonType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** HTTP request body for creating an addon (FR-05, POST /api/v1/addons, ADMIN only). */
public record CreateAddonRequest(

        @NotBlank @Size(max = 50)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        @NotNull @DecimalMin("0.00")
        BigDecimal price,

        @NotBlank @Size(min = 3, max = 3)
        String currency,

        @NotNull
        AddonType type,

        @Positive
        int validityDays
) {
}
