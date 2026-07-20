package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.UpdateFraudRuleCommand;
import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Updates the admin-tunable parameters of one fixed rule code (Feature 23.3.3, ADR-029 Section 4).
 * An unknown/unparseable rule code -> 404 ({@link ResourceNotFoundException}); a valid but not-seeded
 * code -> 404 as well. Adding a genuinely new rule type stays a code change, not a config change. The
 * change is persisted to {@code fraud_rule} and, because rule evaluation (Feature 23.2) reads each rule
 * fresh from the repository per evaluation, it takes effect on the next evaluation with no restart.
 */
@Component
public class UpdateFraudRuleCommandHandler
        implements CommandHandler<UpdateFraudRuleCommand, FraudRuleResponse> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(UpdateFraudRuleCommandHandler.class);

    private final FraudRuleRepository ruleRepository;

    public UpdateFraudRuleCommandHandler(FraudRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    @Transactional
    public FraudRuleResponse handle(UpdateFraudRuleCommand command) {
        FraudRuleCode code = parseCode(command.code());

        FraudRule rule = ruleRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Fraud rule not found with code: " + command.code(),
                        Map.of("code", command.code())));

        rule.updateConfiguration(command.windowMinutes(), command.thresholdCount(),
                command.severity(), command.enabled());
        ruleRepository.save(rule);

        LOGGER.info("Updated fraud rule code={} windowMinutes={} thresholdCount={} severity={} enabled={}",
                code, command.windowMinutes(), command.thresholdCount(), command.severity(),
                command.enabled());
        return FraudRuleResponse.from(rule);
    }

    private static FraudRuleCode parseCode(String rawCode) {
        try {
            return FraudRuleCode.valueOf(rawCode);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    "Fraud rule not found with code: " + rawCode,
                    Map.of("code", rawCode));
        }
    }
}
