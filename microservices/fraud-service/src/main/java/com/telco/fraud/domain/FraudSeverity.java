package com.telco.fraud.domain;

/**
 * Severity of a {@link FraudRule} and the {@link FraudSignal}s it raises (design-note.md Section 6).
 * MVP defaults per ADR-029 Section 4: {@code RAPID_SIM_SWAP -> HIGH}, {@code MSISDN_CHURN_VELOCITY ->
 * MEDIUM}, {@code SUSPEND_REACTIVATE_VELOCITY -> LOW}. This enum only names the values.
 */
public enum FraudSeverity {
    LOW,
    MEDIUM,
    HIGH
}
