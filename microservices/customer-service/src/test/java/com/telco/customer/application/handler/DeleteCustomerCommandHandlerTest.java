package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.DeleteCustomerCommand;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.Unit;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteCustomerCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private AuditLogWriter audit;

    private DeleteCustomerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeleteCustomerCommandHandler(customers, audit);
    }

    private Customer aCustomer() {
        return Customer.register(CustomerType.INDIVIDUAL, "Grace", "Hopper", "10000000146",
                LocalDate.of(1985, 5, 5), null, null);
    }

    @Test
    void softDeletesCustomerAndWritesAuditRow() {
        Customer customer = aCustomer();
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Unit result = handler.handle(new DeleteCustomerCommand(customer.getId()));

        assertThat(result).isEqualTo(Unit.INSTANCE);
        assertThat(customer.isDeleted()).isTrue();
        verify(audit).log(eq("CUSTOMER_DELETED"), eq("Customer"), eq(customer.getId().toString()), eq(null));
    }

    @Test
    void throwsResourceNotFoundWhenCustomerDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(customers.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new DeleteCustomerCommand(missing)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
