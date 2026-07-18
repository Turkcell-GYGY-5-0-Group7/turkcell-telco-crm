package com.telco.fraud.application.command;

import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.platform.cqrs.Command;

/**
 * Updates the admin-tunable parameters of one fixed {@link com.telco.fraud.domain.FraudRule} code
 * (Feature 23.3.3, ADR-029 Section 4) without a redeploy. {@code code} is the raw path-variable string;
 * an unknown/unparseable code is rejected with 404 ({@code ResourceNotFoundException}) by the handler -
 * adding a genuinely new rule type remains a code change, not a config change.
 */
public record UpdateFraudRuleCommand(
        String code,
        int windowMinutes,
        int thresholdCount,
        FraudSeverity severity,
        boolean enabled
) implements Command<FraudRuleResponse> {
}
