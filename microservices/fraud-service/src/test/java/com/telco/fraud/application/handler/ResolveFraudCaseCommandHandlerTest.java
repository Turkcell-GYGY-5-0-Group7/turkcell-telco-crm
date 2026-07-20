package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.ResolveFraudCaseCommand;
import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.application.event.FraudCaseResolvedV1;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveFraudCaseCommandHandlerTest {

    @Mock private FraudCaseRepository caseRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private OutboxService outboxService;

    private ResolveFraudCaseCommandHandler handler;

    private final UUID caseId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final String agentId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        handler = new ResolveFraudCaseCommandHandler(
                caseRepository, currentUserProvider, outboxService);
    }

    private FraudCase openCase() {
        return new FraudCase(caseId, customerId, FraudCaseStatus.OPEN,
                new ArrayList<>(List.of(UUID.randomUUID())), Instant.now(), null, null);
    }

    private void authenticatedAgent() {
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(agentId, Set.of("SUPPORT"), null, null));
    }

    @Test
    void confirmed_transitions_case_stamps_resolver_and_publishes_event() {
        FraudCase fraudCase = openCase();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fraudCase));
        authenticatedAgent();

        FraudCaseSummaryResponse result = handler.handle(
                new ResolveFraudCaseCommand(caseId, FraudCaseStatus.CONFIRMED, "confirmed by review"));

        assertThat(fraudCase.getStatus()).isEqualTo(FraudCaseStatus.CONFIRMED);
        assertThat(fraudCase.getResolvedBy()).isEqualTo(agentId);
        assertThat(fraudCase.getResolvedAt()).isNotNull();
        assertThat(result.status()).isEqualTo("CONFIRMED");
        verify(caseRepository).save(fraudCase);

        ArgumentCaptor<FraudCaseResolvedV1> captor = ArgumentCaptor.forClass(FraudCaseResolvedV1.class);
        verify(outboxService).publish(eq("fraud"), eq(caseId.toString()),
                eq("fraud.case-resolved.v1"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().resolvedBy()).isEqualTo(agentId);
        assertThat(captor.getValue().caseId()).isEqualTo(caseId.toString());
        assertThat(captor.getValue().customerId()).isEqualTo(customerId.toString());
    }

    @Test
    void dismissed_transitions_case_and_publishes_event() {
        FraudCase fraudCase = openCase();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fraudCase));
        authenticatedAgent();

        handler.handle(new ResolveFraudCaseCommand(caseId, FraudCaseStatus.DISMISSED, null));

        assertThat(fraudCase.getStatus()).isEqualTo(FraudCaseStatus.DISMISSED);
        verify(outboxService).publish(eq("fraud"), eq(caseId.toString()),
                eq("fraud.case-resolved.v1"), any(FraudCaseResolvedV1.class));
    }

    @Test
    void unknown_case_id_raises_404() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new ResolveFraudCaseCommand(caseId, FraudCaseStatus.CONFIRMED, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(caseRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void already_resolved_case_is_rejected_with_business_rule_violation() {
        FraudCase resolved = new FraudCase(caseId, customerId, FraudCaseStatus.CONFIRMED,
                new ArrayList<>(), Instant.now(), Instant.now(), "someone");
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(resolved));
        lenient().when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(agentId, Set.of("SUPPORT"), null, null));

        assertThatThrownBy(() -> handler.handle(
                new ResolveFraudCaseCommand(caseId, FraudCaseStatus.DISMISSED, null)))
                .isInstanceOf(BusinessRuleException.class);

        verify(caseRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    /**
     * ADR-029 Section 5 hard scope boundary (feature-level acceptance criterion, sprint 23.5): resolving
     * a case - CONFIRMED or DISMISSED - makes ZERO outbound calls to subscription-service. The handler's
     * ONLY collaborators are the fraud-case repository, the platform current-user provider, and the
     * outbox; there is no subscription-service client to mock because none exists on any reachable code
     * path. After a full CONFIRMED resolution, {@code verifyNoMoreInteractions} proves the handler made
     * no other outbound call (no suspend/hold/mutate against subscription-service). Mirrors
     * {@code EscalateFraudCaseCommandHandlerTest#escalation_makes_zero_outbound_subscription_service_calls}.
     */
    @Test
    void resolution_makes_zero_outbound_subscription_service_calls() {
        FraudCase fraudCase = openCase();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fraudCase));
        authenticatedAgent();

        handler.handle(new ResolveFraudCaseCommand(caseId, FraudCaseStatus.CONFIRMED, "manual review"));

        verify(caseRepository).findById(caseId);
        verify(caseRepository).save(fraudCase);
        verify(currentUserProvider).currentUser();
        verify(outboxService).publish(eq("fraud"), eq(caseId.toString()),
                eq("fraud.case-resolved.v1"), any(FraudCaseResolvedV1.class));
        // No subscription-service call path exists: the mocks above are the complete collaborator set.
        verifyNoMoreInteractions(caseRepository, outboxService);
    }
}
