package com.telco.catalog.application.command;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.domain.model.AddonType;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Creates a new addon in the catalog (FR-05, ADMIN only). */
public record CreateAddonCommand(

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

) implements Command<AddonResponse> {
}
