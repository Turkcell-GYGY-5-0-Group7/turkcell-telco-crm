package com.telco.customer.application.dto;

import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** Profile and contact update input (FR-03). The identity number is immutable and cannot be updated. */
public record UpdateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Past LocalDate dateOfBirth,
        @Email @Size(max = 255) @Sensitive(MaskStrategy.PARTIAL) String email,
        @Size(max = 32) @Sensitive(MaskStrategy.PARTIAL) String phone
) {
}
