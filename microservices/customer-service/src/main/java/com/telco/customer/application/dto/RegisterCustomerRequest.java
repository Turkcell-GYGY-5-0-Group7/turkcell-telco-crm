package com.telco.customer.application.dto;

import com.telco.customer.domain.CustomerType;
import com.telco.customer.domain.validation.ValidTckn;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * Registration input (FR-01). The identity number is validated as a TCKN at the boundary and masked in
 * the log view via {@link Sensitive} (ADR-021). The MVP onboards individual customers; corporate VKN
 * validation is provided separately ({@code @ValidVkn}).
 */
public record RegisterCustomerRequest(
        @NotNull CustomerType type,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @ValidTckn @Sensitive(MaskStrategy.PARTIAL) String identityNumber,
        @Past LocalDate dateOfBirth
) {
}
