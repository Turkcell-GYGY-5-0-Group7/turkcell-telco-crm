package com.telco.customer.application.handler;

import com.telco.customer.application.command.AddAddressCommand;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

/** Adds an address; when default, the previous default is cleared first to satisfy the one-default rule. */
@Component
public class AddAddressCommandHandler implements CommandHandler<AddAddressCommand, AddressResponse> {

    private final CustomerRepository customers;
    private final AddressRepository addresses;

    public AddAddressCommandHandler(CustomerRepository customers, AddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
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
