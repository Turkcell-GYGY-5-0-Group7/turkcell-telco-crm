package com.telco.fraud.application.handler;

import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.application.query.GetFraudCasesQuery;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.platform.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFraudCasesQueryHandlerTest {

    @Mock private FraudCaseRepository caseRepository;

    private GetFraudCasesQueryHandler handler;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new GetFraudCasesQueryHandler(caseRepository);
    }

    private FraudCase caseWith(FraudCaseStatus status) {
        return new FraudCase(UUID.randomUUID(), customerId, status,
                new ArrayList<>(List.of(UUID.randomUUID())), Instant.now(), null, null);
    }

    private Page<FraudCase> pageOf(FraudCase... cases) {
        return new PageImpl<>(List.of(cases));
    }

    @Test
    void no_filters_lists_all_cases() {
        when(caseRepository.findAll(any(Pageable.class)))
                .thenReturn(pageOf(caseWith(FraudCaseStatus.OPEN)));

        PageResult<FraudCaseSummaryResponse> result =
                handler.handle(new GetFraudCasesQuery(null, null, 0, 20));

        assertThat(result.content()).hasSize(1);
        verify(caseRepository).findAll(any(Pageable.class));
    }

    @Test
    void status_filter_only_queries_by_status() {
        when(caseRepository.findByStatus(eq(FraudCaseStatus.OPEN), any(Pageable.class)))
                .thenReturn(pageOf(caseWith(FraudCaseStatus.OPEN)));

        PageResult<FraudCaseSummaryResponse> result =
                handler.handle(new GetFraudCasesQuery(FraudCaseStatus.OPEN, null, 0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo("OPEN");
        verify(caseRepository).findByStatus(eq(FraudCaseStatus.OPEN), any(Pageable.class));
        verify(caseRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void customer_filter_only_queries_by_customer() {
        when(caseRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(pageOf(caseWith(FraudCaseStatus.CONFIRMED)));

        handler.handle(new GetFraudCasesQuery(null, customerId, 0, 20));

        verify(caseRepository).findByCustomerId(eq(customerId), any(Pageable.class));
    }

    @Test
    void both_filters_query_by_customer_and_status() {
        when(caseRepository.findByCustomerIdAndStatus(
                eq(customerId), eq(FraudCaseStatus.OPEN), any(Pageable.class)))
                .thenReturn(pageOf(caseWith(FraudCaseStatus.OPEN)));

        handler.handle(new GetFraudCasesQuery(FraudCaseStatus.OPEN, customerId, 0, 20));

        verify(caseRepository).findByCustomerIdAndStatus(
                eq(customerId), eq(FraudCaseStatus.OPEN), any(Pageable.class));
    }
}
