package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EvaluateMsisdnChurnVelocityCommand;
import com.telco.fraud.application.command.EvaluateRapidSimSwapCommand;
import com.telco.fraud.application.command.EvaluateSuspendReactivateVelocityCommand;
import com.telco.fraud.application.command.IngestLifecycleSignalCommand;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.domain.MsisdnLifecycleSignal;
import com.telco.fraud.infrastructure.persistence.MsisdnLifecycleSignalRepository;
import com.telco.platform.cqrs.Command;
import com.telco.platform.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestLifecycleSignalCommandHandlerTest {

    @Mock private MsisdnLifecycleSignalRepository signalRepository;
    @Mock private Mediator mediator;

    private IngestLifecycleSignalCommandHandler handler;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        handler = new IngestLifecycleSignalCommandHandler(signalRepository, mediator);
    }

    @Test
    void allocation_persists_row_and_dispatches_rapid_and_churn_evaluators() {
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        handler.handle(new IngestLifecycleSignalCommand(
                MsisdnLifecycleEventType.MSISDN_ALLOCATED, customerId, "905550001122",
                subscriptionId, now, null));

        verify(signalRepository).save(any(MsisdnLifecycleSignal.class));
        ArgumentCaptor<Command<?>> captor = ArgumentCaptor.forClass(Command.class);
        verify(mediator, times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(c -> c instanceof EvaluateRapidSimSwapCommand)
                .anyMatch(c -> c instanceof EvaluateMsisdnChurnVelocityCommand);
    }

    @Test
    void release_without_customer_resolves_from_prior_allocation() {
        UUID resolvedCustomerId = UUID.randomUUID();
        MsisdnLifecycleSignal priorAllocation = new MsisdnLifecycleSignal(UUID.randomUUID(),
                MsisdnLifecycleEventType.MSISDN_ALLOCATED, resolvedCustomerId, "905550001122",
                UUID.randomUUID(), now.minusSeconds(600), now.minusSeconds(600), null);
        when(signalRepository.findFirstByMsisdnAndEventTypeAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                eq("905550001122"), eq(MsisdnLifecycleEventType.MSISDN_ALLOCATED), any()))
                .thenReturn(Optional.of(priorAllocation));

        handler.handle(new IngestLifecycleSignalCommand(
                MsisdnLifecycleEventType.MSISDN_RELEASED, null, "905550001122",
                UUID.randomUUID(), now, null));

        ArgumentCaptor<MsisdnLifecycleSignal> savedSignal =
                ArgumentCaptor.forClass(MsisdnLifecycleSignal.class);
        verify(signalRepository).save(savedSignal.capture());
        assertThat(savedSignal.getValue().getCustomerId()).isEqualTo(resolvedCustomerId);

        ArgumentCaptor<Command<?>> captor = ArgumentCaptor.forClass(Command.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(EvaluateMsisdnChurnVelocityCommand.class);
        assertThat(((EvaluateMsisdnChurnVelocityCommand) captor.getValue()).customerId())
                .isEqualTo(resolvedCustomerId);
    }

    @Test
    void suspension_persists_reason_and_dispatches_suspend_reactivate_evaluator() {
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        handler.handle(new IngestLifecycleSignalCommand(
                MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED, customerId, null,
                subscriptionId, now, "NON_PAYMENT"));

        ArgumentCaptor<MsisdnLifecycleSignal> savedSignal =
                ArgumentCaptor.forClass(MsisdnLifecycleSignal.class);
        verify(signalRepository).save(savedSignal.capture());
        assertThat(savedSignal.getValue().getReason()).isEqualTo("NON_PAYMENT");

        ArgumentCaptor<Command<?>> captor = ArgumentCaptor.forClass(Command.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(EvaluateSuspendReactivateVelocityCommand.class);
    }
}
