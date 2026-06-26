package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.RejectKycCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.event.CustomerKycRejectedV1;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Rejects KYC: transitions PENDING -> REJECTED, publishes {@code customer.kyc-rejected.v1}, and writes
 * an audit row (FR-02).
 */
@Component
public class RejectKycCommandHandler implements CommandHandler<RejectKycCommand, CustomerResponse> {

    private static final String AGGREGATE_TYPE = "Customer";
    private static final String EVENT_TYPE = "customer.kyc-rejected.v1";

    private final CustomerRepository customers;
    private final OutboxService outbox;
    private final AuditLogWriter audit;

    public RejectKycCommandHandler(CustomerRepository customers, OutboxService outbox,
                                   AuditLogWriter audit) {
        this.customers = customers;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Override
    public CustomerResponse handle(RejectKycCommand command) {
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "customer not found: " + command.customerId()));

        customer.rejectKyc();
        customers.save(customer);

        String id = customer.getId().toString();
        outbox.publish(AGGREGATE_TYPE, id, EVENT_TYPE, new CustomerKycRejectedV1(
                id, customer.getStatus().name(), command.reason(), Instant.now().toEpochMilli()));

        audit.log("CUSTOMER_KYC_REJECTED", AGGREGATE_TYPE, id,
                command.reason() != null ? Map.of("reason", command.reason()) : null);

        return CustomerResponse.from(customer);
    }
}
