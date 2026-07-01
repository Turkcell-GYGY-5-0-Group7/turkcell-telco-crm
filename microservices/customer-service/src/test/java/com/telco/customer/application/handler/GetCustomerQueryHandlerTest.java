package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.query.GetCustomerQuery;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCustomerQueryHandlerTest {

    @Mock
    private CustomerRepository customers;

    private GetCustomerQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetCustomerQueryHandler(customers);
    }

    @Test
    void returnsCustomerResponseForExistingId() {
        Customer customer = Customer.register(CustomerType.INDIVIDUAL, "Alan", "Turing",
                "10000000146", LocalDate.of(1912, 6, 23));
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        CustomerResponse response = handler.handle(new GetCustomerQuery(customer.getId()));

        assertThat(response.id()).isEqualTo(customer.getId());
        assertThat(response.firstName()).isEqualTo("Alan");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void throwsResourceNotFoundForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(customers.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetCustomerQuery(missing)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
