package com.telco.customer.application.command;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.domain.CustomerType;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import com.telco.platform.cqrs.Command;

import java.time.LocalDate;

/** Registers a new customer in PENDING status and publishes customer.registered.v1 (FR-01). */
public record RegisterCustomerCommand(
        CustomerType type,
        String firstName,
        String lastName,
        @Sensitive(MaskStrategy.PARTIAL) String identityNumber,
        LocalDate dateOfBirth
) implements Command<CustomerResponse> {
}
