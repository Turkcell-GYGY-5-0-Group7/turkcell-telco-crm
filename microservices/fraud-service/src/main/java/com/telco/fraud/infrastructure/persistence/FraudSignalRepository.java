package com.telco.fraud.infrastructure.persistence;

import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for evaluated rule hits ({@link FraudSignal}). Case escalation
 * (Feature 23.3) reads a customer's recent signals to decide whether to open a {@link
 * com.telco.fraud.domain.FraudCase}. Data access only.
 */
public interface FraudSignalRepository extends JpaRepository<FraudSignal, UUID> {

    /** A customer's signals at or after {@code windowStart}, for escalation decisions (Feature 23.3). */
    List<FraudSignal> findByCustomerIdAndTriggeredAtGreaterThanEqual(UUID customerId, Instant windowStart);

    /** A customer's signals for one rule code, most recent first. */
    List<FraudSignal> findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(UUID customerId, FraudRuleCode ruleCode);
}
