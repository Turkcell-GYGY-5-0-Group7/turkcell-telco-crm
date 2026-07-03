package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.AddAddressCommand;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddAddressCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private AddressRepository addresses;
    @Mock
    private AuditLogWriter audit;

    private AddAddressCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddAddressCommandHandler(customers, addresses, audit);
    }

    @Test
    void addsNonDefaultAddressWithoutClearingPreviousDefault() {
        UUID customerId = UUID.randomUUID();
        when(customers.existsById(customerId)).thenReturn(true);
        when(addresses.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddAddressCommand command = new AddAddressCommand(
                customerId, "Sok 1", "Istanbul", "Kadikoy", "34000", false);

        AddressResponse response = handler.handle(command);

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.city()).isEqualTo("Istanbul");
        assertThat(response.isDefault()).isFalse();
        verify(addresses, never()).findByCustomerIdAndIsDefaultTrue(any());
        verify(audit).log(eq("ADDRESS_ADDED"), eq("Address"), anyString(), any());
    }

    @Test
    void clearsExistingDefaultBeforeAddingNewDefaultAddress() {
        UUID customerId = UUID.randomUUID();
        Address existingDefault = Address.create(customerId, "Old St", "Ankara", "Cankaya", "06000", true);
        when(customers.existsById(customerId)).thenReturn(true);
        when(addresses.findByCustomerIdAndIsDefaultTrue(customerId))
                .thenReturn(Optional.of(existingDefault));
        when(addresses.saveAndFlush(existingDefault)).thenReturn(existingDefault);
        when(addresses.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddAddressCommand command = new AddAddressCommand(
                customerId, "New St", "Istanbul", "Besiktas", "34100", true);

        AddressResponse response = handler.handle(command);

        assertThat(response.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isFalse();
        verify(addresses).saveAndFlush(existingDefault);
    }

    @Test
    void throwsResourceNotFoundWhenCustomerDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(customers.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(
                new AddAddressCommand(missing, "Sok 1", "Istanbul", "Kadikoy", "34000", false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
