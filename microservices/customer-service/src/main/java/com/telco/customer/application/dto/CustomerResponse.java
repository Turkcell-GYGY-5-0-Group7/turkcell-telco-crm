package com.telco.customer.application.dto;

import com.telco.customer.domain.Customer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read DTO for a customer (ADR-015). The identity number is never returned in full: only a masked form
 * showing the last four characters is exposed (NFR-06, ADR-021). Domain entities are not exposed.
 */
public record CustomerResponse(
        UUID id,
        String type,
        String firstName,
        String lastName,
        String identityNumberMasked,
        LocalDate dateOfBirth,
        String email,
        String phone,
        String status,
        Instant createdAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getType().name(),
                customer.getFirstName(),
                customer.getLastName(),
                mask(customer.getIdentityNumber()),
                customer.getDateOfBirth(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getStatus().name(),
                customer.getCreatedAt()
        );
    }

    /** Masks all but the last four characters, e.g. {@code *******8901}. */
    private static String mask(String value) {
        if (value == null) {
            return null;
        }
        int keep = Math.min(4, value.length());
        int maskedLen = value.length() - keep;
        return "*".repeat(maskedLen) + value.substring(maskedLen);
    }
}
