package com.telco.fraud.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudRuleTest {

    private FraudRule rule() {
        return new FraudRule(FraudRuleCode.RAPID_SIM_SWAP, 60, 3, FraudSeverity.HIGH, true);
    }

    @Test
    void update_configuration_applies_new_parameters() {
        FraudRule rule = rule();

        rule.updateConfiguration(120, 5, FraudSeverity.MEDIUM, false);

        assertThat(rule.getWindowMinutes()).isEqualTo(120);
        assertThat(rule.getThresholdCount()).isEqualTo(5);
        assertThat(rule.getSeverity()).isEqualTo(FraudSeverity.MEDIUM);
        assertThat(rule.isEnabled()).isFalse();
        assertThat(rule.getCode()).isEqualTo(FraudRuleCode.RAPID_SIM_SWAP);
    }

    @Test
    void update_configuration_rejects_non_positive_window() {
        FraudRule rule = rule();

        assertThatThrownBy(() -> rule.updateConfiguration(0, 5, FraudSeverity.HIGH, true))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_configuration_rejects_non_positive_threshold() {
        FraudRule rule = rule();

        assertThatThrownBy(() -> rule.updateConfiguration(60, 0, FraudSeverity.HIGH, true))
                .isInstanceOf(BusinessRuleException.class);
    }
}
