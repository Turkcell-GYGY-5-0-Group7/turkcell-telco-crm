package com.telco.customer.application.dto;

import com.telco.customer.domain.CustomerType;
import com.telco.customer.domain.validation.IdentityBearing;
import com.telco.customer.domain.validation.ValidIdentityForType;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * Registration input (FR-01). The identity number is validated against the algorithm matching the
 * customer type (TCKN for INDIVIDUAL, VKN for CORPORATE) via the class-level
 * {@link ValidIdentityForType}, and masked in the log view via {@link Sensitive} (ADR-021).
 */
@ValidIdentityForType
public record RegisterCustomerRequest(
        @NotNull CustomerType type,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Sensitive(MaskStrategy.PARTIAL) String identityNumber,
        @Past LocalDate dateOfBirth
) implements IdentityBearing {
}
