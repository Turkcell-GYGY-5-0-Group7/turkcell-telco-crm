package com.telco.customer.application.command;

import com.telco.customer.application.dto.AddressResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Updates the fields of an existing address (FR-03). */
public record UpdateAddressCommand(
        UUID customerId,
        UUID addressId,
        String line1,
        String city,
        String district,
        String postalCode
) implements Command<AddressResponse> {
}
