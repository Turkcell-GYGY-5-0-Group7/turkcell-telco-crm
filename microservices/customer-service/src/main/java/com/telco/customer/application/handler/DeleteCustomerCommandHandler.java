package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.DeleteCustomerCommand;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.springframework.stereotype.Component;

/** Soft-deletes a customer (sets deleted_at) and writes an audit row; unknown id yields 404 (FR-04). */
@Component
public class DeleteCustomerCommandHandler implements CommandHandler<DeleteCustomerCommand, Unit> {

    private static final String AGGREGATE_TYPE = "Customer";

    private final CustomerRepository customers;
    private final AuditLogWriter audit;

    public DeleteCustomerCommandHandler(CustomerRepository customers, AuditLogWriter audit) {
        this.customers = customers;
        this.audit = audit;
    }

    @Override
    public Unit handle(DeleteCustomerCommand command) {
        Customer customer = customers.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException("customer not found: " + command.id()));

        customer.markDeleted();
        customers.save(customer);

        audit.log("CUSTOMER_DELETED", AGGREGATE_TYPE, customer.getId().toString(), null);

        return Unit.INSTANCE;
    }
}
