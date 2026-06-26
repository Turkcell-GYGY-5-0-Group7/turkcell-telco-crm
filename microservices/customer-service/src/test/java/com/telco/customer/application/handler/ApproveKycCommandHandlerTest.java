package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.ApproveKycCommand;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.domain.Document;
import com.telco.customer.domain.DocumentType;
import com.telco.customer.infrastructure.CustomerRepository;
import com.telco.customer.infrastructure.DocumentRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.outbox.OutboxService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproveKycCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private DocumentRepository documents;
    @Mock
    private OutboxService outbox;
    @Mock
    private AuditLogWriter audit;

    private ApproveKycCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApproveKycCommandHandler(customers, documents, outbox, audit);
    }

    private Customer pendingCustomer() {
        return Customer.register(CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146",
                LocalDate.of(1990, 1, 1));
    }

    @Test
    void rejectsApprovalWhenNoDocumentUploaded() {
        Customer customer = pendingCustomer();
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(documents.findByCustomerId(customer.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> handler.handle(new ApproveKycCommand(customer.getId())))
                .isInstanceOf(BusinessRuleException.class);
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    void approvesAndPublishesEventWhenDocumentExists() {
        Customer customer = pendingCustomer();
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(documents.findByCustomerId(customer.getId())).thenReturn(List.of(
                Document.record(customer.getId(), DocumentType.ID_CARD, "key", "image/png", "sum")));

        var response = handler.handle(new ApproveKycCommand(customer.getId()));

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(outbox).publish(eq("Customer"), eq(customer.getId().toString()),
                eq("customer.kyc-approved.v1"), any());
        verify(audit).log(eq("CUSTOMER_KYC_APPROVED"), eq("Customer"),
                eq(customer.getId().toString()), any());
    }
}
