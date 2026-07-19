package com.telco.fraud.infrastructure.persistence;

import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for the rule catalogue ({@link FraudRule}, keyed by
 * {@link FraudRuleCode}). Rule evaluation (Feature 23.2) loads the enabled rules to obtain each
 * rule's admin-tuned window/threshold; the rule-config API (Feature 23.3) reads/updates them. Data
 * access only.
 */
public interface FraudRuleRepository extends JpaRepository<FraudRule, FraudRuleCode> {

    /** All currently-enabled rules, for the evaluator to iterate at ingestion time (Feature 23.2). */
    List<FraudRule> findByEnabledTrue();
}
