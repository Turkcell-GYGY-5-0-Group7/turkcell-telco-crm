package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.RegisterCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.event.CustomerRegisteredV1;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.CustomerRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registers a customer in PENDING, publishes {@code customer.registered.v1} via the outbox, and writes
 * an audit row. The mediator TransactionBehavior makes the JPA insert and the outbox row atomic
 * (ADR-005, ADR-009).
 */
@Component
public class RegisterCustomerCommandHandler
        implements CommandHandler<RegisterCustomerCommand, CustomerResponse> {

    private static final String AGGREGATE_TYPE = "Customer";
    private static final String OUTBOX_AGGREGATE_TYPE = "customer";
    private static final String EVENT_TYPE = "customer.registered.v1";

    private final CustomerRepository customers;
    private final OutboxService outbox;
    private final AuditLogWriter audit;

    public RegisterCustomerCommandHandler(CustomerRepository customers, OutboxService outbox,
                                          AuditLogWriter audit) {
        this.customers = customers;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Override
    public CustomerResponse handle(RegisterCustomerCommand command) {
        Customer customer = Customer.register(command.type(), command.firstName(), command.lastName(),
                command.identityNumber(), command.dateOfBirth());
        customers.save(customer);

        String id = customer.getId().toString();
        outbox.publish(OUTBOX_AGGREGATE_TYPE, id, EVENT_TYPE, CustomerRegisteredV1.of(
                id, customer.getType().name(), customer.getStatus().name(),
                customer.getCreatedAt().toEpochMilli()));

        audit.log("CUSTOMER_REGISTERED", AGGREGATE_TYPE, id, Map.of("type", customer.getType().name()));

        return CustomerResponse.from(customer);
    }
}
