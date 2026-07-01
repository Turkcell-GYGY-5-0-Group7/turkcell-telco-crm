package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.command.UpdateAddressCommand;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateAddressCommandHandlerTest {

    @Mock
    private AddressRepository addresses;

    private UpdateAddressCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateAddressCommandHandler(addresses);
    }

    @Test
    void updatesAddressFieldsAndSaves() {
        UUID customerId = UUID.randomUUID();
        Address address = Address.create(customerId, "Old St", "Ankara", "Cankaya", "06000", false);
        when(addresses.findById(address.getId())).thenReturn(Optional.of(address));
        when(addresses.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAddressCommand command = new UpdateAddressCommand(
                customerId, address.getId(), "New St", "Istanbul", "Besiktas", "34100");

        AddressResponse response = handler.handle(command);

        assertThat(response.line1()).isEqualTo("New St");
        assertThat(response.city()).isEqualTo("Istanbul");
        assertThat(response.district()).isEqualTo("Besiktas");
        assertThat(response.postalCode()).isEqualTo("34100");
        verify(addresses).save(address);
    }

    @Test
    void throwsResourceNotFoundWhenAddressDoesNotExist() {
        UUID customerId = UUID.randomUUID();
        UUID missingAddressId = UUID.randomUUID();
        when(addresses.findById(missingAddressId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new UpdateAddressCommand(
                customerId, missingAddressId, "St", "City", "Dist", "00000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsResourceNotFoundWhenAddressBelongsToDifferentCustomer() {
        UUID ownerCustomerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        Address address = Address.create(ownerCustomerId, "St", "City", "Dist", "00000", false);
        when(addresses.findById(address.getId())).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> handler.handle(new UpdateAddressCommand(
                otherCustomerId, address.getId(), "St", "City", "Dist", "00000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
