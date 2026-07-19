package com.telco.fraud.application.query;

import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.platform.cqrs.Query;

import java.util.List;

/**
 * Lists all {@link com.telco.fraud.domain.FraudRule} rows (Feature 23.3.3) - the three fixed MVP rule
 * codes with their current admin-tuned window/threshold/severity/enabled values. Not paged: the rule
 * set is intentionally fixed and small (ADR-029 Section 4).
 */
public record GetFraudRulesQuery() implements Query<List<FraudRuleResponse>> {
}
