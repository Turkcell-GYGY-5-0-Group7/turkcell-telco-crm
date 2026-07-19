package com.telco.fraud.application.dto;

import com.telco.fraud.domain.FraudRule;

/**
 * Read view of a {@link FraudRule} for the admin rule-config API (Feature 23.3.3). Domain entities are
 * never exposed directly (ADR-015). {@code code}/{@code severity} serialize their enum names.
 */
public record FraudRuleResponse(
        String code,
        int windowMinutes,
        int thresholdCount,
        String severity,
        boolean enabled
) {

    public static FraudRuleResponse from(FraudRule rule) {
        return new FraudRuleResponse(
                rule.getCode().name(),
                rule.getWindowMinutes(),
                rule.getThresholdCount(),
                rule.getSeverity().name(),
                rule.isEnabled());
    }
}
