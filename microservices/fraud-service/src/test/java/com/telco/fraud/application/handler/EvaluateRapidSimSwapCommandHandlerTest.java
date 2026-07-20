package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateRapidSimSwapCommand;
import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.domain.MsisdnLifecycleSignal;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.fraud.infrastructure.persistence.MsisdnLifecycleSignalRepository;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluateRapidSimSwapCommandHandlerTest {

    @Mock private FraudRuleRepository ruleRepository;
    @Mock private MsisdnLifecycleSignalRepository signalRepository;
    @Mock private FraudSignalRepository fraudSignalRepository;
    @Mock private OutboxService outboxService;
    @Mock private Mediator mediator;

    private EvaluateRapidSimSwapCommandHandler handler;

    private final String msisdn = "905550001122";
    private final UUID customerId = UUID.randomUUID();
    private final UUID newSubscriptionId = UUID.randomUUID();
    private final UUID oldSubscriptionId = UUID.randomUUID();
    private final UUID allocatedSignalId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        handler = new EvaluateRapidSimSwapCommandHandler(
                ruleRepository, signalRepository, fraudSignalRepository, outboxService, mediator);
    }

    private void enabledRule() {
        when(ruleRepository.findById(FraudRuleCode.RAPID_SIM_SWAP))
                .thenReturn(Optional.of(new FraudRule(
                        FraudRuleCode.RAPID_SIM_SWAP, 15, 1, FraudSeverity.HIGH, true)));
    }

    private MsisdnLifecycleSignal released(UUID subscriptionId, Instant occurredAt) {
        return new MsisdnLifecycleSignal(UUID.randomUUID(), MsisdnLifecycleEventType.MSISDN_RELEASED,
                customerId, msisdn, subscriptionId, occurredAt, occurredAt, null);
    }

    private EvaluateRapidSimSwapCommand command() {
        return new EvaluateRapidSimSwapCommand(allocatedSignalId, customerId, msisdn, newSubscriptionId, now);
    }

    @Test
    void raises_high_signal_when_msisdn_reallocated_to_different_subscription_within_window() {
        enabledRule();
        MsisdnLifecycleSignal priorRelease = released(oldSubscriptionId, now.minus(5, ChronoUnit.MINUTES));
        when(signalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any())).thenReturn(List.of(priorRelease));

        handler.handle(command());

        ArgumentCaptor<FraudSignal> captor = ArgumentCaptor.forClass(FraudSignal.class);
        verify(fraudSignalRepository).save(captor.capture());
        FraudSignal saved = captor.getValue();
        assertThat(saved.getRuleCode()).isEqualTo(FraudRuleCode.RAPID_SIM_SWAP);
        assertThat(saved.getSeverity()).isEqualTo(FraudSeverity.HIGH);
        assertThat(saved.getSourceSignalIds()).containsExactly(priorRelease.getId(), allocatedSignalId);

        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.signal-raised.v1"), any());
        ArgumentCaptor<EscalateFraudCaseCommand> escalate =
                ArgumentCaptor.forClass(EscalateFraudCaseCommand.class);
        verify(mediator).send(escalate.capture());
        assertThat(escalate.getValue().severity()).isEqualTo(FraudSeverity.HIGH);
        assertThat(escalate.getValue().customerId()).isEqualTo(customerId);
    }

    @Test
    void does_not_raise_when_reallocated_to_same_subscription() {
        enabledRule();
        when(signalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any()))
                .thenReturn(List.of(released(newSubscriptionId, now.minus(5, ChronoUnit.MINUTES))));

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
        verify(mediator, never()).send(any());
    }

    @Test
    void does_not_raise_when_no_prior_release_in_window() {
        enabledRule();
        when(signalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any())).thenReturn(List.of());

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    @Test
    void does_not_raise_when_rule_disabled() {
        when(ruleRepository.findById(FraudRuleCode.RAPID_SIM_SWAP))
                .thenReturn(Optional.of(new FraudRule(
                        FraudRuleCode.RAPID_SIM_SWAP, 15, 1, FraudSeverity.HIGH, false)));

        handler.handle(command());

        verify(signalRepository, never())
                .findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(anyString(), any());
        verify(fraudSignalRepository, never()).save(any());
    }

    // --- 23.5.1 boundary cases added on top of the 23.2 tests above ---
    // Already covered by 23.2: same-subscription-reassignment exclusion
    // (does_not_raise_when_reallocated_to_same_subscription), disabled-rule short-circuit
    // (does_not_raise_when_rule_disabled), and the prior-release-present vs absent pair. The cases
    // below add the exact window-edge boundary (23.5.1: windowMinutes - 1s fires, + 1s does not).

    /**
     * 23.5.1 at-window-edge (inside): the lookback boundary the evaluator hands the repository is
     * exactly {@code occurredAt - windowMinutes}, so a release at {@code windowMinutes - 1s} (>=
     * windowStart) is inside the window and fires. Asserting the exact {@code windowStart} argument is
     * the mock-level form of the boundary; the real inclusive-lower-bound SQL cut-off is exercised
     * end to end in {@code RuleEvaluationIntegrationTest} against Postgres.
     */
    @Test
    void computes_inclusive_window_start_and_fires_for_release_one_second_inside_the_edge() {
        enabledRule(); // windowMinutes = 15
        Instant justInside = now.minus(15, ChronoUnit.MINUTES).plusSeconds(1); // windowMinutes - 1s
        MsisdnLifecycleSignal priorRelease = released(oldSubscriptionId, justInside);
        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        when(signalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any())).thenReturn(List.of(priorRelease));

        handler.handle(command());

        verify(signalRepository).findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), windowStart.capture());
        assertThat(windowStart.getValue()).isEqualTo(now.minus(15, ChronoUnit.MINUTES));
        assertThat(justInside).isAfterOrEqualTo(windowStart.getValue()); // inside -> included by the query
        verify(fraudSignalRepository).save(any(FraudSignal.class));
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.signal-raised.v1"), any());
    }

    /**
     * 23.5.1 at-window-edge (outside): a release at {@code windowMinutes + 1s} falls strictly before
     * {@code windowStart}. The repository's {@code >= windowStart} predicate would exclude it, so the
     * evaluator sees an empty window and raises nothing. Verified here at the handler level via the
     * captured boundary plus an empty repository result modelling that exclusion.
     */
    @Test
    void does_not_fire_for_release_one_second_outside_the_window_edge() {
        enabledRule(); // windowMinutes = 15
        Instant justOutside = now.minus(15, ChronoUnit.MINUTES).minusSeconds(1); // windowMinutes + 1s
        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        // A correct query excludes anything strictly before windowStart, so the evaluator sees nothing.
        when(signalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any())).thenReturn(List.of());

        handler.handle(command());

        verify(signalRepository).findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), windowStart.capture());
        assertThat(windowStart.getValue()).isEqualTo(now.minus(15, ChronoUnit.MINUTES));
        assertThat(justOutside).isBefore(windowStart.getValue()); // outside -> excluded by the query
        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    /**
     * 23.5.1 boundary: a release whose {@code occurredAt} is AFTER the triggering allocation is not a
     * "release then re-allocate" swap and must be ignored even though it is inside the window - the
     * evaluator's {@code !occurredAt.isAfter(allocation)} guard enforces the temporal ordering.
     */
    @Test
    void does_not_fire_when_the_only_release_occurred_after_the_allocation() {
        enabledRule();
        MsisdnLifecycleSignal laterRelease = released(oldSubscriptionId, now.plusSeconds(30));
        when(signalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any())).thenReturn(List.of(laterRelease));

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }
}
