package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.UpdateCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
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
class UpdateCustomerCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private OutboxService outbox;
    @Mock
    private AuditLogWriter audit;

    private UpdateCustomerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateCustomerCommandHandler(customers, outbox, audit);
    }

    private Customer aCustomer() {
        return Customer.register(CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146",
                LocalDate.of(1990, 1, 1));
    }

    @Test
    void updatesProfileFieldsAndPublishesEvent() {
        Customer customer = aCustomer();
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCustomerCommand command = new UpdateCustomerCommand(
                customer.getId(), "Augusta", "Byron", LocalDate.of(1815, 12, 10));

        CustomerResponse response = handler.handle(command);

        assertThat(response.firstName()).isEqualTo("Augusta");
        assertThat(response.lastName()).isEqualTo("Byron");
        verify(outbox).publish(eq("customer"), eq(customer.getId().toString()),
                eq("customer.updated.v1"), any());
        verify(audit).log(eq("CUSTOMER_UPDATED"), eq("Customer"),
                eq(customer.getId().toString()), eq(null));
    }

    @Test
    void throwsResourceNotFoundWhenCustomerDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(customers.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new UpdateCustomerCommand(missing, "A", "B", LocalDate.of(2000, 1, 1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
