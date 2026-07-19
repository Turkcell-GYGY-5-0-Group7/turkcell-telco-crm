package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateMsisdnChurnVelocityCommand;
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
 * {@code MSISDN_CHURN_VELOCITY} evaluator (Feature 23.2.3, ADR-029 Section 4 item 2). After any
 * allocate/release ingest, counts a customer's allocate+release signals inside the rolling window and
 * raises one MEDIUM-severity {@link FraudSignal} when the count exceeds the rule threshold - once per
 * distinct breach, not once per event: a customer already over threshold within the same window is not
 * re-flagged (a fresh breach after the count later drops and re-crosses in a new window is allowed).
 *
 * <p>A release with no resolvable {@code customerId} (ADR-029 Amendment 1) is excluded - the handler
 * no-ops on a null customer. Detect-and-alert only (ADR-029 Section 5): no subscription-service call.
 */
@Component
public class EvaluateMsisdnChurnVelocityCommandHandler
        implements CommandHandler<EvaluateMsisdnChurnVelocityCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EvaluateMsisdnChurnVelocityCommandHandler.class);

    private static final List<MsisdnLifecycleEventType> CHURN_EVENT_TYPES =
            List.of(MsisdnLifecycleEventType.MSISDN_ALLOCATED, MsisdnLifecycleEventType.MSISDN_RELEASED);

    private final FraudRuleRepository ruleRepository;
    private final MsisdnLifecycleSignalRepository signalRepository;
    private final FraudSignalRepository fraudSignalRepository;
    private final OutboxService outboxService;
    private final Mediator mediator;

    public EvaluateMsisdnChurnVelocityCommandHandler(FraudRuleRepository ruleRepository,
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
    public Void handle(EvaluateMsisdnChurnVelocityCommand command) {
        if (command.customerId() == null) {
            // Release with no known prior allocation (Amendment 1): excluded from the velocity count.
            return null;
        }
        FraudRule rule = ruleRepository.findById(FraudRuleCode.MSISDN_CHURN_VELOCITY).orElse(null);
        if (rule == null || !rule.isEnabled()) {
            return null;
        }

        Instant windowStart = command.occurredAt().minus(Duration.ofMinutes(rule.getWindowMinutes()));
        List<MsisdnLifecycleSignal> contributing = signalRepository
                .findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
                        command.customerId(), CHURN_EVENT_TYPES, windowStart);

        if (contributing.size() <= rule.getThresholdCount()) {
            return null;
        }
        if (alreadyRaisedInWindow(command.customerId(), windowStart)) {
            LOGGER.debug("MSISDN_CHURN_VELOCITY already raised for customerId={} within window",
                    command.customerId());
            return null;
        }

        UUID signalId = UUID.randomUUID();
        Instant triggeredAt = Instant.now();
        List<UUID> sourceSignalIds = contributing.stream().map(MsisdnLifecycleSignal::getId).toList();
        FraudSignal fraudSignal = new FraudSignal(
                signalId,
                FraudRuleCode.MSISDN_CHURN_VELOCITY,
                command.customerId(),
                null,
                null,
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
                        FraudRuleCode.MSISDN_CHURN_VELOCITY.name(),
                        command.customerId().toString(),
                        null,
                        null,
                        rule.getSeverity().name(),
                        triggeredAt.toEpochMilli()));
        LOGGER.info("Raised MSISDN_CHURN_VELOCITY signal id={} customerId={} count={}",
                signalId, command.customerId(), contributing.size());

        mediator.send(new EscalateFraudCaseCommand(signalId, command.customerId(), rule.getSeverity()));
        return null;
    }

    /** True when the customer already has a churn-velocity signal whose trigger is inside this window. */
    private boolean alreadyRaisedInWindow(UUID customerId, Instant windowStart) {
        return fraudSignalRepository
                .findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                        customerId, FraudRuleCode.MSISDN_CHURN_VELOCITY)
                .stream()
                .findFirst()
                .map(signal -> !signal.getTriggeredAt().isBefore(windowStart))
                .orElse(false);
    }
}
