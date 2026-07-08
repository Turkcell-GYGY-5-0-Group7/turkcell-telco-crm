package com.telco.customer.application.dto;

import java.util.UUID;

/**
 * Minimal, non-PII read shape for system-to-system existence/status checks (tech-lead ruling
 * 14.1.1). Carries only the customer's id and status - no name, no identity number, masked or
 * otherwise.
 */
public record CustomerInternalResponse(UUID id, String status) {

    public static CustomerInternalResponse from(CustomerResponse customer) {
        return new CustomerInternalResponse(customer.id(), customer.status());
    }
}
