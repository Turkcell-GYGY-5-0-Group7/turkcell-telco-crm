package com.telco.customer.application.command;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.domain.CustomerType;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import com.telco.platform.cqrs.Command;

import java.time.LocalDate;

/**
 * Registers a new customer in PENDING status and publishes customer.registered.v1 (FR-01).
 *
 * <p>{@code registeredByUserId} is resolved at the edge (controller) from the caller's identity for
 * genuine self-service registrations only (caller's roles are exactly {@code SUBSCRIBER}); null for
 * agent/dealer-assisted registrations. See the identity-to-customer linkage ruling in
 * {@code docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md}. This
 * handler must not interpret or validate the value - it is passed through as-is.
 */
public record RegisterCustomerCommand(
        CustomerType type,
        String firstName,
        String lastName,
        @Sensitive(MaskStrategy.PARTIAL) String identityNumber,
        LocalDate dateOfBirth,
        String registeredByUserId
) implements Command<CustomerResponse> {
}
