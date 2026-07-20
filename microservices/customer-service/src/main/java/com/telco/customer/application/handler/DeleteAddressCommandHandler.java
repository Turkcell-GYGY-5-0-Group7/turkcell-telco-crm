package com.telco.customer.application.handler;

import com.telco.customer.application.command.DeleteAddressCommand;
import com.telco.customer.domain.Address;
import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Deletes an address owned by the given customer (FR-03). A hard delete: unlike the customer
 * aggregate (FR-04 soft-delete), addresses carry no retention mandate and the audit_log row
 * preserves the KVKK trace.
 */
@Component
public class DeleteAddressCommandHandler implements CommandHandler<DeleteAddressCommand, Unit> {

    private static final String AGGREGATE_TYPE = "Address";

    private final AddressRepository addresses;
    private final AuditLogWriter audit;

    public DeleteAddressCommandHandler(AddressRepository addresses, AuditLogWriter audit) {
        this.addresses = addresses;
        this.audit = audit;
    }

    @Override
    public Unit handle(DeleteAddressCommand command) {
        Address address = loadOwnedAddress(command.customerId(), command.addressId());
        addresses.delete(address);
        audit.log("ADDRESS_DELETED", AGGREGATE_TYPE, address.getId().toString(),
                Map.of("customerId", command.customerId().toString()));
        return Unit.INSTANCE;
    }

    private Address loadOwnedAddress(UUID customerId, UUID addressId) {
        Address address = addresses.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("address not found: " + addressId));
        if (!address.getCustomerId().equals(customerId)) {
            throw new ResourceNotFoundException("address not found: " + addressId);
        }
        return address;
    }
}
