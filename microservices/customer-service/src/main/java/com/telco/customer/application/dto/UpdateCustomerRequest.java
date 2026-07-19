package com.telco.customer.application.dto;

import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Profile update input (FR-03). The identity number is immutable and cannot be updated. Contact info
 * is optional (feature 24.5) and masked in the log view via {@link Sensitive} (ADR-021).
 */
public record UpdateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Past LocalDate dateOfBirth,
        @Email @Size(max = 255) @Sensitive(MaskStrategy.EMAIL) String email,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$") @Size(max = 32)
        @Sensitive(MaskStrategy.PARTIAL) String phone
) {
}
