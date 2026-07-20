package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateSuspendReactivateVelocityCommand;
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
class EvaluateSuspendReactivateVelocityCommandHandlerTest {

    @Mock private FraudRuleRepository ruleRepository;
    @Mock private MsisdnLifecycleSignalRepository signalRepository;
    @Mock private FraudSignalRepository fraudSignalRepository;
    @Mock private OutboxService outboxService;
    @Mock private Mediator mediator;

    private EvaluateSuspendReactivateVelocityCommandHandler handler;

    private final UUID customerId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        handler = new EvaluateSuspendReactivateVelocityCommandHandler(
                ruleRepository, signalRepository, fraudSignalRepository, outboxService, mediator);
    }

    private void enabledRule() {
        when(ruleRepository.findById(FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY))
                .thenReturn(Optional.of(new FraudRule(
                        FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY, 60, 2, FraudSeverity.LOW, true)));
    }

    private MsisdnLifecycleSignal transition(MsisdnLifecycleEventType type, String reason, int minsAgo) {
        return new MsisdnLifecycleSignal(UUID.randomUUID(), type, customerId, null, subscriptionId,
                now.minus(minsAgo, ChronoUnit.MINUTES), now, reason);
    }

    private EvaluateSuspendReactivateVelocityCommand command() {
        return new EvaluateSuspendReactivateVelocityCommand(
                UUID.randomUUID(), customerId, subscriptionId, now);
    }

    @Test
    void raises_signal_when_cycling_exceeds_threshold() {
        enabledRule();
        when(signalRepository.findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), any())).thenReturn(List.of(
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 30),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, "MANUAL", 20),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 5)));
        when(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                customerId, FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY)).thenReturn(List.of());

        handler.handle(command());

        ArgumentCaptor<FraudSignal> captor = ArgumentCaptor.forClass(FraudSignal.class);
        verify(fraudSignalRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleCode()).isEqualTo(FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY);
        assertThat(captor.getValue().getSeverity()).isEqualTo(FraudSeverity.LOW);
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.signal-raised.v1"), any());
        verify(mediator).send(any(EscalateFraudCaseCommand.class));
    }

    @Test
    void excludes_non_payment_suspensions_from_the_count() {
        enabledRule();
        // 1 NON_PAYMENT suspend (excluded) + 1 activate + 1 MANUAL suspend => counted 2, not > 2.
        when(signalRepository.findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), any())).thenReturn(List.of(
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, "NON_PAYMENT", 40),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 20),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, "MANUAL", 5)));

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    @Test
    void does_not_re_raise_when_subscription_already_flagged_within_window() {
        enabledRule();
        when(signalRepository.findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), any())).thenReturn(List.of(
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 30),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, "MANUAL", 20),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 5)));
        FraudSignal existing = new FraudSignal(UUID.randomUUID(),
                FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY, customerId, null, subscriptionId,
                FraudSeverity.LOW, now.minus(10, ChronoUnit.MINUTES), List.of());
        when(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                customerId, FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY)).thenReturn(List.of(existing));

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    // --- 23.5.1 boundary cases added on top of the 23.2 tests above ---
    // Already covered by 23.2: above-threshold (3 counting transitions over threshold 2,
    // raises_signal_when_cycling_exceeds_threshold) and the NON_PAYMENT exclusion landing the count
    // back at the threshold (excludes_non_payment_suspensions_from_the_count). The cases below add the
    // explicit at-threshold and one-below-threshold cases, the disabled-rule short-circuit (absent from
    // the 23.2 suspend test), and the exact rolling-window-start boundary (default 60 min).

    /** 23.5.1 at-threshold: exactly threshold (2) counting transitions is not above threshold -> no fire. */
    @Test
    void does_not_raise_at_exactly_threshold() {
        enabledRule(); // thresholdCount = 2
        when(signalRepository.findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), any())).thenReturn(List.of(
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, "MANUAL", 30),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 10)));

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    /** 23.5.1 one-below-threshold: a single counting transition is below threshold (2) -> no fire. */
    @Test
    void does_not_raise_when_one_below_threshold() {
        enabledRule();
        when(signalRepository.findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), any())).thenReturn(List.of(
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 10)));

        handler.handle(command());

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    /**
     * 23.5.1 disabled-rule short-circuit (absent from the 23.2 suspend test): with
     * {@code enabled=false} the evaluator must not run the rolling-window lookback and raises nothing.
     */
    @Test
    void does_not_raise_when_rule_disabled() {
        when(ruleRepository.findById(FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY))
                .thenReturn(Optional.of(new FraudRule(
                        FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY, 60, 2, FraudSeverity.LOW, false)));

        handler.handle(command());

        verify(signalRepository, never())
                .findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(any(), any(), any());
        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    /**
     * 23.5.1 rolling-window-start boundary: the subscription-scoped lookback boundary handed to the
     * repository is exactly {@code occurredAt - windowMinutes} (default 60 minutes), so a transition at
     * {@code windowMinutes - 1s} is counted and one at {@code windowMinutes + 1s} is not.
     */
    @Test
    void computes_inclusive_rolling_window_start_for_the_subscription_lookback() {
        enabledRule(); // windowMinutes = 60
        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        when(signalRepository.findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), any())).thenReturn(List.of(
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 30),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, "MANUAL", 20),
                transition(MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED, null, 5)));
        when(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                customerId, FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY)).thenReturn(List.of());

        handler.handle(command());

        verify(signalRepository).findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(subscriptionId), any(), windowStart.capture());
        assertThat(windowStart.getValue()).isEqualTo(now.minus(60, ChronoUnit.MINUTES));
    }
}
