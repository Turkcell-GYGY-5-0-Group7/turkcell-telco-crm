package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.RegisterCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.outbox.OutboxService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterCustomerCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private OutboxService outbox;
    @Mock
    private AuditLogWriter audit;

    private RegisterCustomerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegisterCustomerCommandHandler(customers, outbox, audit);
    }

    @Test
    void registersCustomerInPendingStatusAndPublishesEvent() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterCustomerCommand command = new RegisterCustomerCommand(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146", LocalDate.of(1990, 1, 1));

        CustomerResponse response = handler.handle(command);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
        assertThat(response.type()).isEqualTo("INDIVIDUAL");
        verify(outbox).publish(eq("customer"), any(), eq("customer.registered.v1"), any());
        verify(audit).log(eq("CUSTOMER_REGISTERED"), eq("Customer"), any(), any());
    }
}
