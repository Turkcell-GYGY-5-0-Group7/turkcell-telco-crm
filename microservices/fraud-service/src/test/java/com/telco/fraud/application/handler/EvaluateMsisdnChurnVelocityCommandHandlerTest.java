package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateMsisdnChurnVelocityCommand;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluateMsisdnChurnVelocityCommandHandlerTest {

    @Mock private FraudRuleRepository ruleRepository;
    @Mock private MsisdnLifecycleSignalRepository signalRepository;
    @Mock private FraudSignalRepository fraudSignalRepository;
    @Mock private OutboxService outboxService;
    @Mock private Mediator mediator;

    private EvaluateMsisdnChurnVelocityCommandHandler handler;

    private final UUID customerId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        handler = new EvaluateMsisdnChurnVelocityCommandHandler(
                ruleRepository, signalRepository, fraudSignalRepository, outboxService, mediator);
    }

    private void enabledRule() {
        when(ruleRepository.findById(FraudRuleCode.MSISDN_CHURN_VELOCITY))
                .thenReturn(Optional.of(new FraudRule(
                        FraudRuleCode.MSISDN_CHURN_VELOCITY, 1440, 3, FraudSeverity.MEDIUM, true)));
    }

    private List<MsisdnLifecycleSignal> churnSignals(int count) {
        List<MsisdnLifecycleSignal> signals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MsisdnLifecycleEventType type = i % 2 == 0
                    ? MsisdnLifecycleEventType.MSISDN_ALLOCATED
                    : MsisdnLifecycleEventType.MSISDN_RELEASED;
            signals.add(new MsisdnLifecycleSignal(UUID.randomUUID(), type, customerId,
                    "9055500011" + i, UUID.randomUUID(), now.minus(i, ChronoUnit.MINUTES),
                    now, null));
        }
        return signals;
    }

    private EvaluateMsisdnChurnVelocityCommand command(UUID customer) {
        return new EvaluateMsisdnChurnVelocityCommand(UUID.randomUUID(), customer, now);
    }

    @Test
    void raises_medium_signal_when_cycles_exceed_threshold() {
        enabledRule();
        when(signalRepository.findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(customerId), any(), any())).thenReturn(churnSignals(4));
        when(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                customerId, FraudRuleCode.MSISDN_CHURN_VELOCITY)).thenReturn(List.of());

        handler.handle(command(customerId));

        ArgumentCaptor<FraudSignal> captor = ArgumentCaptor.forClass(FraudSignal.class);
        verify(fraudSignalRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleCode()).isEqualTo(FraudRuleCode.MSISDN_CHURN_VELOCITY);
        assertThat(captor.getValue().getSeverity()).isEqualTo(FraudSeverity.MEDIUM);
        assertThat(captor.getValue().getSourceSignalIds()).hasSize(4);
        verify(outboxService).publish(eq("fraud"), anyString(), eq("fraud.signal-raised.v1"), any());
        verify(mediator).send(any(EscalateFraudCaseCommand.class));
    }

    @Test
    void does_not_raise_when_at_or_below_threshold() {
        enabledRule();
        when(signalRepository.findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(customerId), any(), any())).thenReturn(churnSignals(3));

        handler.handle(command(customerId));

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    @Test
    void does_not_re_raise_when_already_flagged_within_same_window() {
        enabledRule();
        when(signalRepository.findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(customerId), any(), any())).thenReturn(churnSignals(5));
        FraudSignal existing = new FraudSignal(UUID.randomUUID(), FraudRuleCode.MSISDN_CHURN_VELOCITY,
                customerId, null, null, FraudSeverity.MEDIUM, now.minus(30, ChronoUnit.MINUTES),
                List.of());
        when(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                customerId, FraudRuleCode.MSISDN_CHURN_VELOCITY)).thenReturn(List.of(existing));

        handler.handle(command(customerId));

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    @Test
    void does_not_raise_for_release_with_no_resolvable_customer() {
        handler.handle(command(null));

        verify(ruleRepository, never()).findById(any());
        verify(fraudSignalRepository, never()).save(any());
    }

    // --- 23.5.1 boundary cases added on top of the 23.2 tests above ---
    // Already covered by 23.2: at-threshold (churnSignals(3), does_not_raise_when_at_or_below_threshold)
    // and above-threshold (churnSignals(4), raises_medium_signal_when_cycles_exceed_threshold). The
    // cases below add the missing one-below-threshold case, the disabled-rule short-circuit (which the
    // 23.2 churn test lacked), and the exact rolling-window-start boundary (default 1440 min / 24h).

    /** 23.5.1 one-below-threshold: with the default threshold of 3, exactly 2 cycles must not fire. */
    @Test
    void does_not_raise_when_one_below_threshold() {
        enabledRule();
        when(signalRepository.findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(customerId), any(), any())).thenReturn(churnSignals(2));

        handler.handle(command(customerId));

        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    /**
     * 23.5.1 disabled-rule short-circuit (absent from the 23.2 churn test): with
     * {@code enabled=false} the evaluator must not even run the rolling-window lookback, and raises
     * nothing regardless of how many cycles exist.
     */
    @Test
    void does_not_raise_when_rule_disabled() {
        when(ruleRepository.findById(FraudRuleCode.MSISDN_CHURN_VELOCITY))
                .thenReturn(Optional.of(new FraudRule(
                        FraudRuleCode.MSISDN_CHURN_VELOCITY, 1440, 3, FraudSeverity.MEDIUM, false)));

        handler.handle(command(customerId));

        verify(signalRepository, never())
                .findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(any(), any(), any());
        verify(fraudSignalRepository, never()).save(any());
        verify(mediator, never()).send(any());
    }

    /**
     * 23.5.1 rolling-window-start boundary: the customer-scoped lookback boundary handed to the
     * repository is exactly {@code occurredAt - windowMinutes} (default 1440 minutes), so a cycle at
     * {@code windowMinutes - 1s} is counted and one at {@code windowMinutes + 1s} is not.
     */
    @Test
    void computes_inclusive_rolling_window_start_for_the_customer_lookback() {
        enabledRule(); // windowMinutes = 1440
        ArgumentCaptor<Instant> windowStart = ArgumentCaptor.forClass(Instant.class);
        when(signalRepository.findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(customerId), any(), any())).thenReturn(churnSignals(4));
        when(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                customerId, FraudRuleCode.MSISDN_CHURN_VELOCITY)).thenReturn(List.of());

        handler.handle(command(customerId));

        verify(signalRepository).findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                eq(customerId), any(), windowStart.capture());
        assertThat(windowStart.getValue()).isEqualTo(now.minus(1440, ChronoUnit.MINUTES));
    }
}
