package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateRapidSimSwapCommand;
import com.telco.fraud.application.event.FraudSignalRaisedV1;
import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.domain.MsisdnLifecycleSignal;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.fraud.infrastructure.persistence.MsisdnLifecycleSignalRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code RAPID_SIM_SWAP} evaluator (Feature 23.2.2, ADR-029 Section 4 item 1). On an ingested
 * {@code MSISDN_ALLOCATED} signal, raises a HIGH-severity {@link FraudSignal} when the same MSISDN was
 * released within the rule window and re-allocated to a DIFFERENT {@code subscriptionId} (Amendment 2:
 * {@code subscriptionId} is the only observable re-assignment key - no SimCard/ICCID on these events).
 *
 * <p>Detect-and-alert only (ADR-029 Section 5): raising the signal writes a row and publishes
 * {@code fraud.signal-raised.v1} via the outbox - it never calls subscription-service or suspends the
 * subscription. Escalation is delegated to {@link EscalateFraudCaseCommand} through the mediator.
 */
@Component
public class EvaluateRapidSimSwapCommandHandler
        implements CommandHandler<EvaluateRapidSimSwapCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EvaluateRapidSimSwapCommandHandler.class);

    // Lowercase outbox aggregate type: the Debezium EventRouter routes each outbox row to
    // `<aggregate_type>.events`, so fraud.* events belong to the `fraud` domain (-> fraud.events),
    // which ticket-service/notification-service subscribe to in Feature 23.4 (ADR-009).
    static final String OUTBOX_AGGREGATE_TYPE = "fraud";
    static final String EVENT_SIGNAL_RAISED = "fraud.signal-raised.v1";

    private final FraudRuleRepository ruleRepository;
    private final MsisdnLifecycleSignalRepository signalRepository;
    private final FraudSignalRepository fraudSignalRepository;
    private final OutboxService outboxService;
    private final Mediator mediator;

    public EvaluateRapidSimSwapCommandHandler(FraudRuleRepository ruleRepository,
                                              MsisdnLifecycleSignalRepository signalRepository,
                                              FraudSignalRepository fraudSignalRepository,
                                              OutboxService outboxService,
                                              Mediator mediator) {
        this.ruleRepository = ruleRepository;
        this.signalRepository = signalRepository;
        this.fraudSignalRepository = fraudSignalRepository;
        this.outboxService = outboxService;
        this.mediator = mediator;
    }

    @Override
    @Transactional
    public Void handle(EvaluateRapidSimSwapCommand command) {
        if (command.msisdn() == null || command.subscriptionId() == null) {
            return null;
        }
        FraudRule rule = ruleRepository.findById(FraudRuleCode.RAPID_SIM_SWAP).orElse(null);
        if (rule == null || !rule.isEnabled()) {
            return null;
        }

        Instant windowStart = command.occurredAt().minus(Duration.ofMinutes(rule.getWindowMinutes()));
        List<MsisdnLifecycleSignal> window = signalRepository
                .findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                        command.msisdn(), windowStart);

        // The most recent MSISDN_RELEASED (at or before the allocation) whose subscription differs
        // from the new allocation's - the re-assignment that defines a rapid SIM swap.
        Optional<MsisdnLifecycleSignal> priorRelease = window.stream()
                .filter(s -> s.getEventType() == MsisdnLifecycleEventType.MSISDN_RELEASED)
                .filter(s -> !s.getId().equals(command.allocatedSignalId()))
                .filter(s -> !s.getOccurredAt().isAfter(command.occurredAt()))
                .filter(s -> s.getSubscriptionId() == null
                        || !s.getSubscriptionId().equals(command.subscriptionId()))
                .reduce((first, second) -> second);

        if (priorRelease.isEmpty()) {
            return null;
        }

        UUID signalId = UUID.randomUUID();
        Instant triggeredAt = Instant.now();
        FraudSignal fraudSignal = new FraudSignal(
                signalId,
                FraudRuleCode.RAPID_SIM_SWAP,
                command.customerId(),
                command.msisdn(),
                command.subscriptionId(),
                rule.getSeverity(),
                triggeredAt,
                List.of(priorRelease.get().getId(), command.allocatedSignalId()));
        fraudSignalRepository.save(fraudSignal);

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, signalId.toString(), EVENT_SIGNAL_RAISED,
                new FraudSignalRaisedV1(
                        signalId.toString(),
                        FraudRuleCode.RAPID_SIM_SWAP.name(),
                        command.customerId() != null ? command.customerId().toString() : null,
                        command.msisdn(),
                        command.subscriptionId().toString(),
                        rule.getSeverity().name(),
                        triggeredAt.toEpochMilli()));
        LOGGER.info("Raised RAPID_SIM_SWAP signal id={} subscriptionId={}",
                signalId, command.subscriptionId());

        mediator.send(new EscalateFraudCaseCommand(signalId, command.customerId(), rule.getSeverity()));
        return null;
    }
}
