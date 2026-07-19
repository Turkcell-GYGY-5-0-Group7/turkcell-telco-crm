package com.telco.fraud.application.handler;

import com.telco.fraud.application.dto.FraudCaseDetailResponse;
import com.telco.fraud.application.query.GetFraudCaseQuery;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFraudCaseQueryHandlerTest {

    @Mock private FraudCaseRepository caseRepository;
    @Mock private FraudSignalRepository signalRepository;

    private GetFraudCaseQueryHandler handler;

    private final UUID caseId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new GetFraudCaseQueryHandler(caseRepository, signalRepository);
    }

    @Test
    void returns_case_with_linked_signals_and_their_source_signal_ids() {
        UUID signalId = UUID.randomUUID();
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        FraudCase fraudCase = new FraudCase(caseId, customerId, FraudCaseStatus.OPEN,
                new ArrayList<>(List.of(signalId)), Instant.now(), null, null);
        FraudSignal signal = new FraudSignal(signalId, FraudRuleCode.RAPID_SIM_SWAP, customerId,
                "905551112233", UUID.randomUUID(), FraudSeverity.HIGH, Instant.now(),
                List.of(sourceA, sourceB));

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fraudCase));
        when(signalRepository.findAllById(List.of(signalId))).thenReturn(List.of(signal));

        FraudCaseDetailResponse response = handler.handle(new GetFraudCaseQuery(caseId));

        assertThat(response.id()).isEqualTo(caseId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.signals()).hasSize(1);
        assertThat(response.signals().get(0).id()).isEqualTo(signalId);
        assertThat(response.signals().get(0).ruleCode()).isEqualTo("RAPID_SIM_SWAP");
        assertThat(response.signals().get(0).sourceSignalIds()).containsExactly(sourceA, sourceB);
    }

    @Test
    void unknown_id_raises_404() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetFraudCaseQuery(caseId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
