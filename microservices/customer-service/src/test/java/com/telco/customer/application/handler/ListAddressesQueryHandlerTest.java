package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.application.query.ListAddressesQuery;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAddressesQueryHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private AddressRepository addresses;

    private ListAddressesQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListAddressesQueryHandler(customers, addresses);
    }

    @Test
    void returnsAddressListForExistingCustomer() {
        UUID customerId = UUID.randomUUID();
        when(customers.existsById(customerId)).thenReturn(true);
        Address a1 = Address.create(customerId, "Sok 1", "Istanbul", "Kadikoy", "34000", true);
        Address a2 = Address.create(customerId, "Sok 2", "Ankara", "Cankaya", "06000", false);
        when(addresses.findByCustomerId(customerId)).thenReturn(List.of(a1, a2));

        List<AddressResponse> result = handler.handle(new ListAddressesQuery(customerId));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AddressResponse::city).containsExactly("Istanbul", "Ankara");
    }

    @Test
    void throwsResourceNotFoundWhenCustomerDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(customers.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new ListAddressesQuery(missing)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
