package com.telco.fraud.domain;

/**
 * The fixed set of MVP fraud-rule codes (ADR-029 Section 4 / design-note.md Section 4). Thresholds
 * and windows for each code are admin-tunable at runtime (stored on {@link FraudRule}); the codes
 * themselves are fixed at this MVP stage - adding a genuinely new rule type is a code change, not a
 * config change. This enum only names the values; rule evaluation is Feature 23.2 work.
 */
public enum FraudRuleCode {
    RAPID_SIM_SWAP,
    MSISDN_CHURN_VELOCITY,
    SUSPEND_REACTIVATE_VELOCITY
}
