package com.telco.customer.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Address add/update input (FR-03). */
public record AddressRequest(
        @NotBlank String line1,
        @NotBlank String city,
        String district,
        String postalCode,
        boolean isDefault
) {
}
