package com.telco.customer.application.command;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import com.telco.platform.cqrs.Command;

import java.time.LocalDate;
import java.util.UUID;

/** Updates a customer's profile fields and publishes customer.updated.v1 (FR-03). */
public record UpdateCustomerCommand(
        UUID id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        @Sensitive(MaskStrategy.EMAIL) String email,
        @Sensitive(MaskStrategy.PARTIAL) String phone
) implements Command<CustomerResponse> {
}
