package com.telco.customer.application.dto;

import com.telco.customer.domain.Address;

import java.util.UUID;

/** Read DTO for a customer address. */
public record AddressResponse(
        UUID id,
        UUID customerId,
        String line1,
        String city,
        String district,
        String postalCode,
        boolean isDefault
) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getCustomerId(),
                address.getLine1(),
                address.getCity(),
                address.getDistrict(),
                address.getPostalCode(),
                address.isDefault()
        );
    }
}
