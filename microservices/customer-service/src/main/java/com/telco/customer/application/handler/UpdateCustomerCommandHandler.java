package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.UpdateCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.event.CustomerUpdatedV1;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Updates profile fields and publishes {@code customer.updated.v1}; unknown id yields 404 (FR-03). */
@Component
public class UpdateCustomerCommandHandler
        implements CommandHandler<UpdateCustomerCommand, CustomerResponse> {

    private static final String AGGREGATE_TYPE = "Customer";
    private static final String OUTBOX_AGGREGATE_TYPE = "customer";
    private static final String EVENT_TYPE = "customer.updated.v1";

    private final CustomerRepository customers;
    private final OutboxService outbox;
    private final AuditLogWriter audit;

    public UpdateCustomerCommandHandler(CustomerRepository customers, OutboxService outbox,
                                        AuditLogWriter audit) {
        this.customers = customers;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Override
    public CustomerResponse handle(UpdateCustomerCommand command) {
        Customer customer = customers.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException("customer not found: " + command.id()));

        customer.updateProfile(command.firstName(), command.lastName(), command.dateOfBirth());
        customer.updateContact(command.email(), command.phone());
        customers.save(customer);

        String id = customer.getId().toString();
        outbox.publish(OUTBOX_AGGREGATE_TYPE, id, EVENT_TYPE, new CustomerUpdatedV1(
                id, customer.getFirstName(), customer.getLastName(), Instant.now().toEpochMilli()));

        audit.log("CUSTOMER_UPDATED", AGGREGATE_TYPE, id, null);

        return CustomerResponse.from(customer);
    }
}
