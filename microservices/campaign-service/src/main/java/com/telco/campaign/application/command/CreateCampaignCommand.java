package com.telco.campaign.application.command;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** Creates a new campaign in DRAFT status (design-note.md Section 5, Feature 21.2.1). */
public record CreateCampaignCommand(

        @NotBlank @Size(max = 50)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        String description,

        @NotNull
        DiscountType discountType,

        @NotNull @DecimalMin("0.00")
        BigDecimal discountValue,

        @NotEmpty
        Set<@NotBlank String> applicableTariffCodes,

        @NotNull
        Instant validFrom,

        @NotNull
        Instant validTo,

        @PositiveOrZero
        Integer totalRedemptionCap,

        @Positive
        int perCustomerRedemptionCap

) implements Command<CampaignResponse> {
}
