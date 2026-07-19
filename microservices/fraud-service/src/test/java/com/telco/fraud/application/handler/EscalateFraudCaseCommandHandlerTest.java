package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalateFraudCaseCommandHandlerTest {

    @Mock private FraudCaseRepository caseRepository;
    @Mock private FraudSignalRepository fraudSignalRepository;
    @Mock private OutboxService outboxService;

    private EscalateFraudCaseCommandHandler handler;

    private final UUID customerId = UUID.randomUUID();
    private final UUID signalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new EscalateFraudCaseCommandHandler(
                caseRepository, fraudSignalRepository, outboxService);
    }

    private void noExistingCase() {
        when(caseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.OPEN)).thenReturn(Optional.empty());
        when(caseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.UNDER_REVIEW)).thenReturn(Optional.empty());
    }

    private FraudSignal signal(UUID id, FraudSeverity severity) {
        return new FraudSignal(id, FraudRuleCode.RAPID_SIM_SWAP, customerId, null, null,
                severity, Instant.now(), List.of());
    }

    @Test
    void high_severity_opens_a_new_case_on_first_occurrence() {
        noExistingCase();
        when(fraudSignalRepository.findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any()))
                .thenReturn(List.of(signal(signalId, FraudSeverity.HIGH)));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.HIGH));

        ArgumentCaptor<FraudCase> captor = ArgumentCaptor.forClass(FraudCase.class);
        verify(caseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FraudCaseStatus.OPEN);
        assertThat(captor.getValue().getSignalIds()).contains(signalId);
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.case-opened.v1"), any());
    }

    @Test
    void attaches_to_existing_open_case_instead_of_opening_duplicate() {
        FraudCase existing = new FraudCase(UUID.randomUUID(), customerId, FraudCaseStatus.OPEN,
                new ArrayList<>(List.of(UUID.randomUUID())), Instant.now(), null, null);
        when(caseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.OPEN)).thenReturn(Optional.of(existing));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.HIGH));

        verify(caseRepository).save(existing);
        assertThat(existing.getSignalIds()).contains(signalId);
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void medium_severity_does_not_escalate_with_only_one_open_window_signal() {
        noExistingCase();
        when(fraudSignalRepository.findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any()))
                .thenReturn(List.of(signal(signalId, FraudSeverity.MEDIUM)));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.MEDIUM));

        verify(caseRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void medium_severity_escalates_with_more_than_one_open_window_signal() {
        noExistingCase();
        when(fraudSignalRepository.findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any()))
                .thenReturn(List.of(
                        signal(UUID.randomUUID(), FraudSeverity.LOW),
                        signal(signalId, FraudSeverity.MEDIUM)));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.MEDIUM));

        ArgumentCaptor<FraudCase> captor = ArgumentCaptor.forClass(FraudCase.class);
        verify(caseRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalIds()).hasSize(2).contains(signalId);
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.case-opened.v1"), any());
    }

    // --- 23.5.1 boundary cases added on top of the 23.2 tests above ---
    // Already covered by 23.2: HIGH first-occurrence opens (high_severity_opens_a_new_case_on_first_occurrence),
    // MEDIUM repetition escalates (medium_severity_escalates_with_more_than_one_open_window_signal),
    // MEDIUM single-signal does not, and attach-to-existing-OPEN (attaches_to_existing_open_case_instead_of_opening_duplicate).
    // The cases below add the second attach path (UNDER_REVIEW) and the LOW-severity repetition path.

    /**
     * 23.5.1 no-duplicate-case-per-customer for a case already {@code UNDER_REVIEW} (the 23.2 test only
     * covered {@code OPEN}): a further qualifying signal must attach to the existing case rather than
     * open a second one, and publishes no new {@code fraud.case-opened.v1}.
     */
    @Test
    void attaches_to_existing_under_review_case_instead_of_opening_duplicate() {
        when(caseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.OPEN)).thenReturn(Optional.empty());
        FraudCase underReview = new FraudCase(UUID.randomUUID(), customerId,
                FraudCaseStatus.UNDER_REVIEW, new ArrayList<>(List.of(UUID.randomUUID())),
                Instant.now(), null, null);
        when(caseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.UNDER_REVIEW)).thenReturn(Optional.of(underReview));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.MEDIUM));

        verify(caseRepository).save(underReview);
        assertThat(underReview.getSignalIds()).contains(signalId);
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    /**
     * 23.5.1 LOW-severity repetition escalates: two open-window signals for the customer cross the
     * more-than-one-signal escalation bar even at LOW severity, opening a case (mirrors the MEDIUM
     * repetition case, exercising the non-HIGH branch for LOW).
     */
    @Test
    void low_severity_escalates_with_more_than_one_open_window_signal() {
        noExistingCase();
        when(fraudSignalRepository.findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any()))
                .thenReturn(List.of(
                        signal(UUID.randomUUID(), FraudSeverity.LOW),
                        signal(signalId, FraudSeverity.LOW)));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.LOW));

        ArgumentCaptor<FraudCase> captor = ArgumentCaptor.forClass(FraudCase.class);
        verify(caseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FraudCaseStatus.OPEN);
        assertThat(captor.getValue().getSignalIds()).hasSize(2).contains(signalId);
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.case-opened.v1"), any());
    }

    /**
     * ADR-029 Section 5 / sprint Exit Criteria bullet 3: the escalation handler is detect-and-alert
     * only. Its ONLY collaborators are the two fraud repositories and the outbox - there is no
     * subscription-service client to mock because none exists on any reachable code path. After a full
     * case-open, {@code verifyNoMoreInteractions} proves the handler made no other outbound call (no
     * suspend/hold/mutate against subscription-service).
     */
    @Test
    void escalation_makes_zero_outbound_subscription_service_calls() {
        noExistingCase();
        when(fraudSignalRepository.findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any()))
                .thenReturn(List.of(signal(signalId, FraudSeverity.HIGH)));

        handler.handle(new EscalateFraudCaseCommand(signalId, customerId, FraudSeverity.HIGH));

        verify(caseRepository).findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.OPEN);
        verify(caseRepository).findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.UNDER_REVIEW);
        verify(caseRepository).save(any(FraudCase.class));
        verify(fraudSignalRepository).findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any());
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.case-opened.v1"), any());
        // No subscription-service call path exists: the mocks above are the complete collaborator set.
        verifyNoMoreInteractions(caseRepository, fraudSignalRepository, outboxService);
    }
}
