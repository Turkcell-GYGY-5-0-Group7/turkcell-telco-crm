package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.UpdateFraudRuleCommand;
import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateFraudRuleCommandHandlerTest {

    @Mock private FraudRuleRepository ruleRepository;

    private UpdateFraudRuleCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateFraudRuleCommandHandler(ruleRepository);
    }

    @Test
    void updates_an_existing_rule_and_persists_the_new_configuration() {
        FraudRule rule = new FraudRule(FraudRuleCode.RAPID_SIM_SWAP, 60, 3, FraudSeverity.HIGH, true);
        when(ruleRepository.findById(FraudRuleCode.RAPID_SIM_SWAP)).thenReturn(Optional.of(rule));

        FraudRuleResponse response = handler.handle(new UpdateFraudRuleCommand(
                "RAPID_SIM_SWAP", 120, 5, FraudSeverity.MEDIUM, false));

        assertThat(rule.getWindowMinutes()).isEqualTo(120);
        assertThat(rule.getThresholdCount()).isEqualTo(5);
        assertThat(rule.getSeverity()).isEqualTo(FraudSeverity.MEDIUM);
        assertThat(rule.isEnabled()).isFalse();
        assertThat(response.code()).isEqualTo("RAPID_SIM_SWAP");
        assertThat(response.windowMinutes()).isEqualTo(120);
        verify(ruleRepository).save(rule);
    }

    @Test
    void unparseable_rule_code_raises_404_without_touching_repository() {
        assertThatThrownBy(() -> handler.handle(new UpdateFraudRuleCommand(
                "NOT_A_REAL_RULE", 120, 5, FraudSeverity.MEDIUM, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ruleRepository, never()).save(any());
    }

    @Test
    void valid_but_missing_rule_code_raises_404() {
        when(ruleRepository.findById(FraudRuleCode.MSISDN_CHURN_VELOCITY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new UpdateFraudRuleCommand(
                "MSISDN_CHURN_VELOCITY", 1440, 4, FraudSeverity.MEDIUM, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ruleRepository, never()).save(any());
    }
}
