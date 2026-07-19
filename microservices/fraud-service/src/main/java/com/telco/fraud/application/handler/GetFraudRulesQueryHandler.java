package com.telco.fraud.application.handler;

import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.fraud.application.query.GetFraudRulesQuery;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Lists all {@link com.telco.fraud.domain.FraudRule} rows (Feature 23.3.3) ordered by code for a
 * stable response. {@code @Transactional(readOnly = true)}: the {@code Mediator}'s
 * {@code TransactionBehavior} wraps commands, not queries; keeping the read transaction open is the
 * consistent pattern applied across the query handlers here ({@code docs/tasks/lessons.md} 2026-07-06).
 */
@Component
public class GetFraudRulesQueryHandler
        implements QueryHandler<GetFraudRulesQuery, List<FraudRuleResponse>> {

    private final FraudRuleRepository ruleRepository;

    public GetFraudRulesQueryHandler(FraudRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FraudRuleResponse> handle(GetFraudRulesQuery query) {
        return ruleRepository.findAll().stream()
                .sorted(Comparator.comparing(rule -> rule.getCode().name()))
                .map(FraudRuleResponse::from)
                .toList();
    }
}
