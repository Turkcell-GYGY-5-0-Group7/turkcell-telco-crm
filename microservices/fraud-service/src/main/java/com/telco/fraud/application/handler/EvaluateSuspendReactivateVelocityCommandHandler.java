package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateSuspendReactivateVelocityCommand;
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
import java.util.UUID;

/**
 * {@code SUSPEND_REACTIVATE_VELOCITY} evaluator (Feature 23.2.4, ADR-029 Section 4 item 3). After a
 * suspend/activate ingest, counts suspend+reactivate transitions for the same {@code subscriptionId}
 * inside the rule window and raises one signal at the rule's configured severity when the count
 * exceeds the threshold - once per distinct breach (a subscription already over threshold within the
 * same window is not re-flagged).
 *
 * <p>Amendment 3: {@code NON_PAYMENT} suspensions are EXCLUDED from the count so legitimate
 * dunning-cycle suspend/reactivate does not trip the rule. The {@code reason} is read from the
 * persisted {@link MsisdnLifecycleSignal} rows (the {@code subscription.suspended.v1} payload carries
 * it and it is stored at ingest, see 23.2.1), so historical suspensions are filtered too, not just the
 * triggering event.
 *
 * <p>Detect-and-alert only (ADR-029 Section 5): the handler never re-suspends or re-activates the
 * subscription and makes no subscription-service call.
 */
@Component
public class EvaluateSuspendReactivateVelocityCommandHandler
        implements CommandHandler<EvaluateSuspendReactivateVelocityCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EvaluateSuspendReactivateVelocityCommandHandler.class);

    /** Suspension reason excluded from the velocity count (ADR-029 Amendment 3). */
    static final String REASON_NON_PAYMENT = "NON_PAYMENT";

    private static final List<MsisdnLifecycleEventType> CYCLE_EVENT_TYPES = List.of(
            MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED,
            MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED);

    private final FraudRuleRepository ruleRepository;
    private final MsisdnLifecycleSignalRepository signalRepository;
    private final FraudSignalRepository fraudSignalRepository;
    private final OutboxService outboxService;
    private final Mediator mediator;

    public EvaluateSuspendReactivateVelocityCommandHandler(
            FraudRuleRepository ruleRepository,
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
    public Void handle(EvaluateSuspendReactivateVelocityCommand command) {
        if (command.subscriptionId() == null) {
            return null;
        }
        FraudRule rule = ruleRepository.findById(FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY).orElse(null);
        if (rule == null || !rule.isEnabled()) {
            return null;
        }

        Instant windowStart = command.occurredAt().minus(Duration.ofMinutes(rule.getWindowMinutes()));
        List<MsisdnLifecycleSignal> transitions = signalRepository
                .findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                        command.subscriptionId(), CYCLE_EVENT_TYPES, windowStart)
                .stream()
                .filter(this::countsTowardVelocity)
                .toList();

        if (transitions.size() <= rule.getThresholdCount()) {
            return null;
        }
        if (alreadyRaisedInWindow(command.customerId(), command.subscriptionId(), windowStart)) {
            LOGGER.debug("SUSPEND_REACTIVATE_VELOCITY already raised for subscriptionId={} within window",
                    command.subscriptionId());
            return null;
        }

        UUID signalId = UUID.randomUUID();
        Instant triggeredAt = Instant.now();
        List<UUID> sourceSignalIds = transitions.stream().map(MsisdnLifecycleSignal::getId).toList();
        FraudSignal fraudSignal = new FraudSignal(
                signalId,
                FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY,
                command.customerId(),
                null,
                command.subscriptionId(),
                rule.getSeverity(),
                triggeredAt,
                sourceSignalIds);
        fraudSignalRepository.save(fraudSignal);

        outboxService.publish(
                EvaluateRapidSimSwapCommandHandler.OUTBOX_AGGREGATE_TYPE,
                signalId.toString(),
                EvaluateRapidSimSwapCommandHandler.EVENT_SIGNAL_RAISED,
                new FraudSignalRaisedV1(
                        signalId.toString(),
                        FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY.name(),
                        command.customerId() != null ? command.customerId().toString() : null,
                        null,
                        command.subscriptionId().toString(),
                        rule.getSeverity().name(),
                        triggeredAt.toEpochMilli()));
        LOGGER.info("Raised SUSPEND_REACTIVATE_VELOCITY signal id={} subscriptionId={} count={}",
                signalId, command.subscriptionId(), transitions.size());

        mediator.send(new EscalateFraudCaseCommand(signalId, command.customerId(), rule.getSeverity()));
        return null;
    }

    /** A reactivation always counts; a suspension counts unless its reason is NON_PAYMENT (Amendment 3). */
    private boolean countsTowardVelocity(MsisdnLifecycleSignal signal) {
        if (signal.getEventType() == MsisdnLifecycleEventType.SUBSCRIPTION_SUSPENDED) {
            return !REASON_NON_PAYMENT.equalsIgnoreCase(signal.getReason());
        }
        return true;
    }

    private boolean alreadyRaisedInWindow(UUID customerId, UUID subscriptionId, Instant windowStart) {
        if (customerId == null) {
            return false;
        }
        return fraudSignalRepository
                .findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                        customerId, FraudRuleCode.SUSPEND_REACTIVATE_VELOCITY)
                .stream()
                .filter(signal -> subscriptionId.equals(signal.getSubscriptionId()))
                .findFirst()
                .map(signal -> !signal.getTriggeredAt().isBefore(windowStart))
                .orElse(false);
    }
}
