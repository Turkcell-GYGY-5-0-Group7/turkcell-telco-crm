package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.RejectKycCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerStatus;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RejectKycCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private OutboxService outbox;
    @Mock
    private AuditLogWriter audit;

    private RejectKycCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RejectKycCommandHandler(customers, outbox, audit);
    }

    private Customer pendingCustomer() {
        return Customer.register(CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146",
                LocalDate.of(1990, 1, 1), null, null);
    }

    @Test
    void rejectsKycAndPublishesEventWithReason() {
        Customer customer = pendingCustomer();
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = handler.handle(
                new RejectKycCommand(customer.getId(), "Documents expired"));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(outbox).publish(eq("customer"), eq(customer.getId().toString()),
                eq("customer.kyc-rejected.v1"), any());
        verify(audit).log(eq("CUSTOMER_KYC_REJECTED"), eq("Customer"),
                eq(customer.getId().toString()), any());
    }

    @Test
    void rejectsKycWithNullReasonDoesNotIncludeReasonInAuditDetails() {
        Customer customer = pendingCustomer();
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = handler.handle(
                new RejectKycCommand(customer.getId(), null));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(outbox).publish(eq("customer"), eq(customer.getId().toString()),
                eq("customer.kyc-rejected.v1"), any());
        verify(audit).log(eq("CUSTOMER_KYC_REJECTED"), eq("Customer"),
                eq(customer.getId().toString()), eq(null));
    }

    @Test
    void throwsResourceNotFoundWhenCustomerDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(customers.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RejectKycCommand(missing, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throwsBusinessRuleExceptionWhenCustomerIsNotPending() {
        Customer customer = pendingCustomer();
        customer.approveKyc();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);

        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> handler.handle(new RejectKycCommand(customer.getId(), "too late")))
                .isInstanceOf(BusinessRuleException.class);
        verify(outbox, never()).publish(any(), any(), any(), any());
    }
}
