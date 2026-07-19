package com.telco.fraud.application.handler;

import com.telco.fraud.application.dto.FraudCaseDetailResponse;
import com.telco.fraud.application.dto.FraudSignalResponse;
import com.telco.fraud.application.query.GetFraudCaseQuery;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Single fraud-case view with its linked signals (Feature 23.3.1). Loads the case, then its
 * {@link FraudSignal}s by the case's {@code signalIds}, and for each surfaces the contributing
 * {@link com.telco.fraud.domain.MsisdnLifecycleSignal} ids for agent traceability (design-note.md
 * Section 6). Unknown id -> 404 via the platform {@link ResourceNotFoundException} (reused, not
 * hand-rolled). {@code @Transactional(readOnly = true)}: the {@code Mediator}'s
 * {@code TransactionBehavior} wraps commands, not queries, so the read transaction keeps the session
 * open while the DTO mappers copy each signal's {@code sourceSignalIds} ({@code docs/tasks/lessons.md}
 * 2026-07-06).
 */
@Component
public class GetFraudCaseQueryHandler
        implements QueryHandler<GetFraudCaseQuery, FraudCaseDetailResponse> {

    private final FraudCaseRepository caseRepository;
    private final FraudSignalRepository signalRepository;

    public GetFraudCaseQueryHandler(FraudCaseRepository caseRepository,
                                    FraudSignalRepository signalRepository) {
        this.caseRepository = caseRepository;
        this.signalRepository = signalRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FraudCaseDetailResponse handle(GetFraudCaseQuery query) {
        FraudCase fraudCase = caseRepository.findById(query.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Fraud case not found with id: " + query.id(),
                        Map.of("id", query.id().toString())));

        List<FraudSignalResponse> signals = signalRepository.findAllById(fraudCase.getSignalIds())
                .stream()
                .map(FraudSignalResponse::from)
                .toList();

        return FraudCaseDetailResponse.of(fraudCase, signals);
    }
}
