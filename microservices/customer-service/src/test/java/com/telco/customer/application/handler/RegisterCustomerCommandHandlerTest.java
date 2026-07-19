package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.RegisterCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.event.CustomerRegisteredV1;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.outbox.OutboxService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146", LocalDate.of(1990, 1, 1),
                null, null, null);

        CustomerResponse response = handler.handle(command);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
        assertThat(response.type()).isEqualTo("INDIVIDUAL");
        verify(outbox).publish(eq("customer"), any(), eq("customer.registered.v1"), any());
        verify(audit).log(eq("CUSTOMER_REGISTERED"), eq("Customer"), any(), any());
    }

    @Test
    void registersCustomerWithContactInfoAndCarriesItOnResponseAndEvent() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterCustomerCommand command = new RegisterCustomerCommand(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146", LocalDate.of(1990, 1, 1),
                "ada@example.com", "+905321112233", null);

        CustomerResponse response = handler.handle(command);

        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.phone()).isEqualTo("+905321112233");

        ArgumentCaptor<Customer> savedCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(savedCaptor.getValue().getPhone()).isEqualTo("+905321112233");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.email()).isEqualTo("ada@example.com");
        assertThat(event.phone()).isEqualTo("+905321112233");
    }

    @Test
    void registersCustomerWithoutContactInfoLeavesEventContactFieldsNull() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new RegisterCustomerCommand(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146", LocalDate.of(1990, 1, 1),
                null, null, null));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.email()).isNull();
        assertThat(event.phone()).isNull();
    }

    @Test
    void selfServiceRegistrationPassesCallerUserIdThroughToEvent() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        String callerUserId = UUID.randomUUID().toString();

        RegisterCustomerCommand command = new RegisterCustomerCommand(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146", LocalDate.of(1990, 1, 1),
                null, null, callerUserId);

        handler.handle(command);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.registeredByUserId()).isEqualTo(callerUserId);
    }

    @Test
    void agentAssistedRegistrationLeavesRegisteredByUserIdNullOnEvent() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterCustomerCommand command = new RegisterCustomerCommand(
                CustomerType.INDIVIDUAL, "Grace", "Hopper", "10000000146", LocalDate.of(1990, 1, 1),
                null, null, null);

        handler.handle(command);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.registeredByUserId()).isNull();
    }
}
