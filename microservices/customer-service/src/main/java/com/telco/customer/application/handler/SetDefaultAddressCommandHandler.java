package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.SetDefaultAddressCommand;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Makes one address the default, clearing the previous default (FR-03). */
@Component
public class SetDefaultAddressCommandHandler
        implements CommandHandler<SetDefaultAddressCommand, Unit> {

    private static final String AGGREGATE_TYPE = "Address";

    private final AddressRepository addresses;
    private final AuditLogWriter audit;

    public SetDefaultAddressCommandHandler(AddressRepository addresses, AuditLogWriter audit) {
        this.addresses = addresses;
        this.audit = audit;
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

        audit.log("ADDRESS_SET_DEFAULT", AGGREGATE_TYPE, target.getId().toString(),
                Map.of("customerId", command.customerId().toString()));

        return Unit.INSTANCE;
    }
}
