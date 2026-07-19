package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telco.customer.application.query.ListCustomersQuery;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.ValidationException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ListCustomersQueryHandlerTest {

    @Mock
    private CustomerRepository customers;

    private ListCustomersQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListCustomersQueryHandler(customers);
    }

    @Test
    void returnsPagedResultMappedFromCustomers() {
        Customer c1 = Customer.register(CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146",
                LocalDate.of(1990, 1, 1), null, null);
        Customer c2 = Customer.register(CustomerType.INDIVIDUAL, "Grace", "Hopper", "11111111110",
                LocalDate.of(1985, 5, 5), null, null);
        when(customers.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1, c2)));

        PageResult<?> result = handler.handle(new ListCustomersQuery(0, 10, null));

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.totalElements()).isEqualTo(2L);
    }

    @Test
    void returnsEmptyPageWhenNoCustomersExist() {
        when(customers.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResult<?> result = handler.handle(new ListCustomersQuery(0, 10, null));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
    }

    @Test
    void absentSortDefaultsToCreatedAtDesc() {
        when(customers.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        handler.handle(new ListCustomersQuery(0, 10, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customers).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void explicitSortIsAppliedToTheRepositoryCall() {
        when(customers.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        handler.handle(new ListCustomersQuery(0, 10, "lastName,asc"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customers).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "lastName"));
    }

    @Test
    void unknownSortPropertyRaisesValidationError() {
        assertThatThrownBy(() -> handler.handle(new ListCustomersQuery(0, 10, "identityNumber,asc")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(customers);
    }
}
