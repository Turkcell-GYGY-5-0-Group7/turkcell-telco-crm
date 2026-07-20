package com.telco.fraud.infrastructure.persistence;

import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for escalated cases ({@link FraudCase}). Escalation (Feature 23.3) looks
 * up a customer's existing open case to avoid duplicating one; the fraud-case API (Feature 23.3) lists
 * and resolves cases. Data access only.
 */
public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {

    /** Cases for a customer in a given status (e.g. all {@code OPEN} cases). */
    List<FraudCase> findByCustomerIdAndStatus(UUID customerId, FraudCaseStatus status);

    /** The most recent case for a customer in a given status, for escalation de-duplication (23.3). */
    Optional<FraudCase> findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
            UUID customerId, FraudCaseStatus status);

    /** Paged listing filtered by status only (Feature 23.3.1). */
    Page<FraudCase> findByStatus(FraudCaseStatus status, Pageable pageable);

    /** Paged listing filtered by customer only (Feature 23.3.1). */
    Page<FraudCase> findByCustomerId(UUID customerId, Pageable pageable);

    /** Paged listing filtered by both customer and status (Feature 23.3.1). */
    Page<FraudCase> findByCustomerIdAndStatus(UUID customerId, FraudCaseStatus status, Pageable pageable);
}
