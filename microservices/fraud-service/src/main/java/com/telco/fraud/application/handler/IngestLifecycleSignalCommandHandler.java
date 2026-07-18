package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EvaluateMsisdnChurnVelocityCommand;
import com.telco.fraud.application.command.EvaluateRapidSimSwapCommand;
import com.telco.fraud.application.command.EvaluateSuspendReactivateVelocityCommand;
import com.telco.fraud.application.command.IngestLifecycleSignalCommand;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.domain.MsisdnLifecycleSignal;
import com.telco.fraud.infrastructure.persistence.MsisdnLifecycleSignalRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.mediator.Mediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Appends the raw {@link MsisdnLifecycleSignal} log row (Feature 23.2.1) and then triggers the rule
 * evaluators that key off the event type (Feature 23.2.2-23.2.4), each dispatched through the mediator
 * (ADR-008). Runs inside the mediator's transaction (TransactionBehavior, PROPAGATION_REQUIRED), so
 * the appended row, any raised {@code FraudSignal}/{@code FraudCase}, and their outbox rows commit
 * atomically with this ingest.
 *
 * <p>Defensive {@code customerId} resolution (ADR-029 Amendment 1): an {@code MSISDN_RELEASED} event
 * that arrived without a {@code customerId} (older producers) is backfilled from the most recent prior
 * {@code MSISDN_ALLOCATED} row for the same MSISDN. A release with no resolvable customer keeps a null
 * customer and is therefore excluded from the customer-keyed {@code MSISDN_CHURN_VELOCITY} count.
 */
@Component
public class IngestLifecycleSignalCommandHandler
        implements CommandHandler<IngestLifecycleSignalCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IngestLifecycleSignalCommandHandler.class);

    private final MsisdnLifecycleSignalRepository signalRepository;
    private final Mediator mediator;

    public IngestLifecycleSignalCommandHandler(MsisdnLifecycleSignalRepository signalRepository,
                                               Mediator mediator) {
        this.signalRepository = signalRepository;
        this.mediator = mediator;
    }

    @Override
    @Transactional
    public Void handle(IngestLifecycleSignalCommand command) {
        UUID customerId = resolveCustomerId(command);

        UUID signalId = UUID.randomUUID();
        MsisdnLifecycleSignal signal = new MsisdnLifecycleSignal(
                signalId,
                command.eventType(),
                customerId,
                command.msisdn(),
                command.subscriptionId(),
                command.occurredAt(),
                Instant.now(),
                command.reason());
        signalRepository.save(signal);
        LOGGER.info("Ingested lifecycle signal id={} type={} customerId={} subscriptionId={}",
                signalId, command.eventType(), customerId, command.subscriptionId());

        dispatchEvaluators(command, signalId, customerId);
        return null;
    }

    private UUID resolveCustomerId(IngestLifecycleSignalCommand command) {
        if (command.customerId() != null) {
            return command.customerId();
        }
        if (command.eventType() == MsisdnLifecycleEventType.MSISDN_RELEASED && command.msisdn() != null) {
            return signalRepository
                    .findFirstByMsisdnAndEventTypeAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                            command.msisdn(), MsisdnLifecycleEventType.MSISDN_ALLOCATED,
                            command.occurredAt())
                    .map(MsisdnLifecycleSignal::getCustomerId)
                    .orElse(null);
        }
        return null;
    }

    private void dispatchEvaluators(IngestLifecycleSignalCommand command, UUID signalId,
                                    UUID customerId) {
        switch (command.eventType()) {
            case MSISDN_ALLOCATED -> {
                mediator.send(new EvaluateRapidSimSwapCommand(
                        signalId, customerId, command.msisdn(), command.subscriptionId(),
                        command.occurredAt()));
                mediator.send(new EvaluateMsisdnChurnVelocityCommand(
                        signalId, customerId, command.occurredAt()));
            }
            case MSISDN_RELEASED -> mediator.send(new EvaluateMsisdnChurnVelocityCommand(
                    signalId, customerId, command.occurredAt()));
            case SUBSCRIPTION_ACTIVATED, SUBSCRIPTION_SUSPENDED ->
                    mediator.send(new EvaluateSuspendReactivateVelocityCommand(
                            signalId, customerId, command.subscriptionId(), command.occurredAt()));
            default -> LOGGER.debug("No evaluator wired for event type {}", command.eventType());
        }
    }
}
