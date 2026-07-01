package com.telco.customer.application.handler;

import com.telco.customer.application.command.UpdateAddressCommand;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

/** Updates the fields of an address that belongs to the given customer (FR-03). */
@Component
public class UpdateAddressCommandHandler
        implements CommandHandler<UpdateAddressCommand, AddressResponse> {

    private final AddressRepository addresses;

    public UpdateAddressCommandHandler(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    public AddressResponse handle(UpdateAddressCommand command) {
        Address address = loadOwnedAddress(command.customerId(), command.addressId());
        address.update(command.line1(), command.city(), command.district(), command.postalCode());
        addresses.save(address);
        return AddressResponse.from(address);
    }

    private Address loadOwnedAddress(java.util.UUID customerId, java.util.UUID addressId) {
        Address address = addresses.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("address not found: " + addressId));
        if (!address.getCustomerId().equals(customerId)) {
            throw new ResourceNotFoundException("address not found: " + addressId);
        }
        return address;
    }
}
