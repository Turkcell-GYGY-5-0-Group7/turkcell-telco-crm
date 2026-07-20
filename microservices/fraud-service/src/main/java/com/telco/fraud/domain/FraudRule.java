package com.telco.fraud.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A single fraud-rule configuration (design-note.md Section 6, ADR-029 Section 4). The rule
 * {@code code} is the primary key; {@code windowMinutes} and {@code thresholdCount} are the
 * admin-tunable evaluation parameters, tunable at runtime via 23.3's rule-config API without a
 * redeploy. Seeded with three default rows in {@code V2__fraud_rule_seed.sql}. Bare JPA mapping only
 * as of Feature 23.1 - no domain behavior methods yet.
 */
@Entity
@Table(name = "fraud_rule")
public class FraudRule {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 40)
    private FraudRuleCode code;

    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes;

    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private FraudSeverity severity;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** For JPA only. */
    protected FraudRule() {
    }

    public FraudRule(FraudRuleCode code, int windowMinutes, int thresholdCount,
                     FraudSeverity severity, boolean enabled) {
        this.code = Objects.requireNonNull(code, "code");
        this.windowMinutes = windowMinutes;
        this.thresholdCount = thresholdCount;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.enabled = enabled;
    }

    /**
     * Applies an admin threshold/window/severity/enabled update (Feature 23.3.3, ADR-029 Section 4).
     * The rule {@code code} itself is fixed and never changes here - only its tunable parameters -
     * so ops can adjust false-positive/false-negative rates without a redeploy. The next rule
     * evaluation (Feature 23.2) reads the rule fresh from the repository, so the change takes effect
     * live with no service restart. {@code windowMinutes} and {@code thresholdCount} must be positive.
     */
    public void updateConfiguration(int windowMinutes, int thresholdCount, FraudSeverity severity,
                                    boolean enabled) {
        if (windowMinutes <= 0) {
            throw new BusinessRuleException("windowMinutes must be positive, was " + windowMinutes);
        }
        if (thresholdCount <= 0) {
            throw new BusinessRuleException("thresholdCount must be positive, was " + thresholdCount);
        }
        this.windowMinutes = windowMinutes;
        this.thresholdCount = thresholdCount;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.enabled = enabled;
    }

    public FraudRuleCode getCode() {
        return code;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public int getThresholdCount() {
        return thresholdCount;
    }

    public FraudSeverity getSeverity() {
        return severity;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
