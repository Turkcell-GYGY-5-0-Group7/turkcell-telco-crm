package com.telco.customer.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** Profile update input (FR-03). The identity number is immutable and cannot be updated. */
public record UpdateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Past LocalDate dateOfBirth
) {
}
