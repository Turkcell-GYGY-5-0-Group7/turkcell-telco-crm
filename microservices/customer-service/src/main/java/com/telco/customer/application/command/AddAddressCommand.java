package com.telco.customer.application.command;

import com.telco.customer.application.dto.AddressResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Adds an address to a customer; when default, the previous default is cleared (FR-03). */
public record AddAddressCommand(
        UUID customerId,
        String line1,
        String city,
        String district,
        String postalCode,
        boolean isDefault
) implements Command<AddressResponse> {
}
