package com.telco.fraud.application.dto;

import com.telco.fraud.domain.FraudSeverity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code PUT /api/v1/fraud-rules/{code}} (Feature 23.3.3, ADR-029 Section 4). Carries
 * the admin-tunable parameters of a fixed rule code: rolling-window length, hit threshold, severity,
 * and whether the rule is active. The rule {@code code} itself is a path variable and is not mutable
 * (adding a new code is a code change, not config).
 */
public record UpdateFraudRuleRequest(
        @Positive int windowMinutes,
        @Positive int thresholdCount,
        @NotNull FraudSeverity severity,
        @NotNull Boolean enabled
) {
}
