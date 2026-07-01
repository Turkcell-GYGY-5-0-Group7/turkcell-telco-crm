package com.telco.customer.infrastructure.persistence;

import com.telco.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence for the {@link Customer} aggregate. Soft-delete is enforced on the entity via
 * {@code @SQLRestriction("deleted_at IS NULL")} (FR-04), so every derived/default query here
 * transparently excludes deleted customers while their rows are retained.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
}
