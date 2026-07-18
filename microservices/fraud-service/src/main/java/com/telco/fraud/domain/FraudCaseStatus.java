package com.telco.fraud.domain;

/**
 * Lifecycle status of a {@link FraudCase} (design-note.md Section 6). {@code OPEN} on escalation;
 * an agent moves it through {@code UNDER_REVIEW} to a terminal {@code CONFIRMED} or {@code DISMISSED}
 * outcome. State-transition behavior is added in a later Sprint 23 feature - this enum only names the
 * states.
 */
public enum FraudCaseStatus {
    OPEN,
    UNDER_REVIEW,
    CONFIRMED,
    DISMISSED
}
