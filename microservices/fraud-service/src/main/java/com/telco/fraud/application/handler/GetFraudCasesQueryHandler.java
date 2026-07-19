package com.telco.fraud.application.handler;

import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.application.query.GetFraudCasesQuery;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paged fraud-case listing (Feature 23.3.1), applying the optional {@code status} and {@code customerId}
 * filters. {@code @Transactional(readOnly = true)}: the {@code Mediator}'s {@code TransactionBehavior}
 * wraps commands, not queries, so an explicit read transaction keeps the Hibernate session open while
 * {@link FraudCaseSummaryResponse#from} reads each case ({@code docs/tasks/lessons.md} 2026-07-06).
 */
@Component
public class GetFraudCasesQueryHandler
        implements QueryHandler<GetFraudCasesQuery, PageResult<FraudCaseSummaryResponse>> {

    private final FraudCaseRepository caseRepository;

    public GetFraudCasesQueryHandler(FraudCaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FraudCaseSummaryResponse> handle(GetFraudCasesQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());

        Page<FraudCase> cases;
        if (query.customerId() != null && query.status() != null) {
            cases = caseRepository.findByCustomerIdAndStatus(query.customerId(), query.status(), pageable);
        } else if (query.customerId() != null) {
            cases = caseRepository.findByCustomerId(query.customerId(), pageable);
        } else if (query.status() != null) {
            cases = caseRepository.findByStatus(query.status(), pageable);
        } else {
            cases = caseRepository.findAll(pageable);
        }

        Page<FraudCaseSummaryResponse> page = cases.map(FraudCaseSummaryResponse::from);
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
