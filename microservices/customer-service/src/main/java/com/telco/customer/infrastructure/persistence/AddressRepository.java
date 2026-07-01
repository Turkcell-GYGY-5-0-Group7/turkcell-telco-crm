package com.telco.customer.infrastructure.persistence;

import com.telco.customer.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for customer {@link Address} entities (FR-03). */
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByCustomerId(UUID customerId);

    Optional<Address> findByCustomerIdAndIsDefaultTrue(UUID customerId);
}
