package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.UpdateAddressCommand;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Updates the fields of an address that belongs to the given customer (FR-03). */
@Component
public class UpdateAddressCommandHandler
        implements CommandHandler<UpdateAddressCommand, AddressResponse> {

    private static final String AGGREGATE_TYPE = "Address";

    private final AddressRepository addresses;
    private final AuditLogWriter audit;

    public UpdateAddressCommandHandler(AddressRepository addresses, AuditLogWriter audit) {
        this.addresses = addresses;
        this.audit = audit;
    }

    @Override
    public AddressResponse handle(UpdateAddressCommand command) {
        Address address = loadOwnedAddress(command.customerId(), command.addressId());
        address.update(command.line1(), command.city(), command.district(), command.postalCode());
        addresses.save(address);

        audit.log("ADDRESS_UPDATED", AGGREGATE_TYPE, address.getId().toString(),
                Map.of("customerId", command.customerId().toString()));

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
