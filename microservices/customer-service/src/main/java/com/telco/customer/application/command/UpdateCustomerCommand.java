package com.telco.customer.application.command;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.platform.cqrs.Command;

import java.time.LocalDate;
import java.util.UUID;

/** Updates a customer's profile and contact fields and publishes customer.updated.v1 (FR-03). */
public record UpdateCustomerCommand(
        UUID id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String email,
        String phone
) implements Command<CustomerResponse> {

    /** Legacy shape without contact fields - both default to null (cleared). */
    public UpdateCustomerCommand(UUID id, String firstName, String lastName, LocalDate dateOfBirth) {
        this(id, firstName, lastName, dateOfBirth, null, null);
    }
}
