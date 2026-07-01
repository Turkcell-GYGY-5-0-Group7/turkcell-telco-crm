package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.ApproveKycCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.event.CustomerKycApprovedV1;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.customer.infrastructure.persistence.DocumentRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Approves KYC: requires at least one uploaded document, transitions PENDING -> ACTIVE, publishes
 * {@code customer.kyc-approved.v1}, and writes an audit row (FR-02, AC-01 step 3).
 */
@Component
public class ApproveKycCommandHandler implements CommandHandler<ApproveKycCommand, CustomerResponse> {

    private static final String AGGREGATE_TYPE = "Customer";
    private static final String OUTBOX_AGGREGATE_TYPE = "customer";
    private static final String EVENT_TYPE = "customer.kyc-approved.v1";

    private final CustomerRepository customers;
    private final DocumentRepository documents;
    private final OutboxService outbox;
    private final AuditLogWriter audit;

    public ApproveKycCommandHandler(CustomerRepository customers, DocumentRepository documents,
                                    OutboxService outbox, AuditLogWriter audit) {
        this.customers = customers;
        this.documents = documents;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Override
    public CustomerResponse handle(ApproveKycCommand command) {
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "customer not found: " + command.customerId()));

        if (documents.findByCustomerId(command.customerId()).isEmpty()) {
            throw new BusinessRuleException("cannot approve KYC without an uploaded document");
        }

        customer.approveKyc();
        customers.save(customer);

        String id = customer.getId().toString();
        outbox.publish(OUTBOX_AGGREGATE_TYPE, id, EVENT_TYPE, new CustomerKycApprovedV1(
                id, customer.getStatus().name(), Instant.now().toEpochMilli()));

        audit.log("CUSTOMER_KYC_APPROVED", AGGREGATE_TYPE, id, null);

        return CustomerResponse.from(customer);
    }
}
