package com.telco.fraud.application.handler;

import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.fraud.application.query.GetFraudRulesQuery;
import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFraudRulesQueryHandlerTest {

    @Mock private FraudRuleRepository ruleRepository;

    private GetFraudRulesQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetFraudRulesQueryHandler(ruleRepository);
    }

    @Test
    void returns_all_rules_sorted_by_code() {
        when(ruleRepository.findAll()).thenReturn(List.of(
                new FraudRule(FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY, 1440, 2, FraudSeverity.LOW, true),
                new FraudRule(FraudRuleCode.RAPID_SIM_SWAP, 60, 3, FraudSeverity.HIGH, true),
                new FraudRule(FraudRuleCode.MSISDN_CHURN_VELOCITY, 1440, 4, FraudSeverity.MEDIUM, false)));

        List<FraudRuleResponse> result = handler.handle(new GetFraudRulesQuery());

        assertThat(result).extracting(FraudRuleResponse::code)
                .containsExactly("MSISDN_CHURN_VELOCITY", "RAPID_SIM_SWAP", "SUSPEND_REACTIVATE_VELOCITY");
        assertThat(result).extracting(FraudRuleResponse::severity)
                .containsExactly("MEDIUM", "HIGH", "LOW");
        assertThat(result.get(1).windowMinutes()).isEqualTo(60);
        assertThat(result.get(1).thresholdCount()).isEqualTo(3);
        assertThat(result.get(2).enabled()).isTrue();
    }
}
