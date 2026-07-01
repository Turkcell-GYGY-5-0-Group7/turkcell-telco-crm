package com.telco.customer.application.handler;

import com.telco.customer.application.command.SetDefaultAddressCommand;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.springframework.stereotype.Component;

/** Makes one address the default, clearing the previous default (FR-03). */
@Component
public class SetDefaultAddressCommandHandler
        implements CommandHandler<SetDefaultAddressCommand, Unit> {

    private final AddressRepository addresses;

    public SetDefaultAddressCommandHandler(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    public Unit handle(SetDefaultAddressCommand command) {
        Address target = addresses.findById(command.addressId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "address not found: " + command.addressId()));
        if (!target.getCustomerId().equals(command.customerId())) {
            throw new ResourceNotFoundException("address not found: " + command.addressId());
        }

        addresses.findByCustomerIdAndIsDefaultTrue(command.customerId()).ifPresent(current -> {
            if (!current.getId().equals(target.getId())) {
                current.clearDefault();
                // Flush before setting the new default: the partial unique index is not deferrable.
                addresses.saveAndFlush(current);
            }
        });

        target.makeDefault();
        addresses.save(target);

        return Unit.INSTANCE;
    }
}
