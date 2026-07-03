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
import com.telco.customer.application.command.SetDefaultAddressCommand;
import com.telco.customer.domain.Address;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.Unit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetDefaultAddressCommandHandlerTest {

    @Mock
    private AddressRepository addresses;
    @Mock
    private AuditLogWriter audit;

    private SetDefaultAddressCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SetDefaultAddressCommandHandler(addresses, audit);
    }

    @Test
    void makesTargetDefaultAndClearsExistingDefault() {
        UUID customerId = UUID.randomUUID();
        Address previous = Address.create(customerId, "Old St", "Ankara", "Cankaya", "06000", true);
        Address target = Address.create(customerId, "New St", "Istanbul", "Kadikoy", "34000", false);

        when(addresses.findById(target.getId())).thenReturn(Optional.of(target));
        when(addresses.findByCustomerIdAndIsDefaultTrue(customerId)).thenReturn(Optional.of(previous));
        when(addresses.saveAndFlush(previous)).thenReturn(previous);
        when(addresses.save(target)).thenReturn(target);

        Unit result = handler.handle(new SetDefaultAddressCommand(customerId, target.getId()));

        assertThat(result).isEqualTo(Unit.INSTANCE);
        assertThat(previous.isDefault()).isFalse();
        assertThat(target.isDefault()).isTrue();
        verify(addresses).saveAndFlush(previous);
        verify(addresses).save(target);
        verify(audit).log(eq("ADDRESS_SET_DEFAULT"), eq("Address"), anyString(), any());
    }

    @Test
    void skipsFlushWhenTargetIsAlreadyTheCurrentDefault() {
        UUID customerId = UUID.randomUUID();
        Address target = Address.create(customerId, "St", "City", "Dist", "00000", true);

        when(addresses.findById(target.getId())).thenReturn(Optional.of(target));
        when(addresses.findByCustomerIdAndIsDefaultTrue(customerId)).thenReturn(Optional.of(target));
        when(addresses.save(target)).thenReturn(target);

        handler.handle(new SetDefaultAddressCommand(customerId, target.getId()));

        verify(addresses, never()).saveAndFlush(any());
        verify(addresses).save(target);
    }

    @Test
    void throwsResourceNotFoundWhenAddressDoesNotExist() {
        UUID customerId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(addresses.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SetDefaultAddressCommand(customerId, missingId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsResourceNotFoundWhenAddressBelongsToDifferentCustomer() {
        UUID ownerCustomerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        Address address = Address.create(ownerCustomerId, "St", "City", "Dist", "00000", false);
        when(addresses.findById(address.getId())).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> handler.handle(
                new SetDefaultAddressCommand(otherCustomerId, address.getId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
