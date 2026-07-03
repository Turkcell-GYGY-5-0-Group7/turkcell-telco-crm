package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.AddAddressCommand;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Adds an address; when default, the previous default is cleared first to satisfy the one-default rule. */
@Component
public class AddAddressCommandHandler implements CommandHandler<AddAddressCommand, AddressResponse> {

    private static final String AGGREGATE_TYPE = "Address";

    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final AuditLogWriter audit;

    public AddAddressCommandHandler(CustomerRepository customers, AddressRepository addresses,
                                    AuditLogWriter audit) {
        this.customers = customers;
        this.addresses = addresses;
        this.audit = audit;
    }

    @Override
    public AddressResponse handle(AddAddressCommand command) {
        if (!customers.existsById(command.customerId())) {
            throw new ResourceNotFoundException("customer not found: " + command.customerId());
        }

        if (command.isDefault()) {
            clearExistingDefault(command.customerId());
        }

        Address address = addresses.save(Address.create(command.customerId(), command.line1(),
                command.city(), command.district(), command.postalCode(), command.isDefault()));

        audit.log("ADDRESS_ADDED", AGGREGATE_TYPE, address.getId().toString(),
                Map.of("customerId", command.customerId().toString(), "isDefault", command.isDefault()));

        return AddressResponse.from(address);
    }

    private void clearExistingDefault(java.util.UUID customerId) {
        addresses.findByCustomerIdAndIsDefaultTrue(customerId).ifPresent(current -> {
            current.clearDefault();
            // Flush the cleared default before inserting the new one: the partial unique index
            // (customer_id) WHERE is_default is not deferrable.
            addresses.saveAndFlush(current);
        });
    }
}
