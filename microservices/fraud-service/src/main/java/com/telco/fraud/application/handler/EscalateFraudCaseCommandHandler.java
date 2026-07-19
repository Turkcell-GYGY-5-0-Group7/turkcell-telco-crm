package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.event.FraudCaseOpenedV1;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code FraudCase} escalation (Feature 23.2.5, ADR-029 Section 5). After a {@link FraudSignal} is
 * raised: a HIGH-severity signal always opens a case on the customer's first occurrence (when none is
 * {@code OPEN}/{@code UNDER_REVIEW}); a MEDIUM/LOW signal escalates only once the customer has more
 * than one open-window signal. If an {@code OPEN}/{@code UNDER_REVIEW} case already exists, the new
 * signal is attached to it instead of opening a duplicate. A newly opened case publishes
 * {@code fraud.case-opened.v1} via the outbox.
 *
 * <p><strong>Detect-and-alert only (ADR-029 Section 5, sprint Exit Criteria bullet 3):</strong> this
 * handler's ONLY collaborators are the fraud-owned repositories and the outbox. There is no
 * RestClient/WebClient/Feign client to subscription-service anywhere on any code path reachable from
 * here - opening a case never suspends, holds, or otherwise mutates a subscription. Any suspension
 * remains a manual, out-of-band agent action.
 */
@Component
public class EscalateFraudCaseCommandHandler
        implements CommandHandler<EscalateFraudCaseCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EscalateFraudCaseCommandHandler.class);

    static final String EVENT_CASE_OPENED = "fraud.case-opened.v1";

    // Look-back for accumulating a customer's open-window signals and gathering signal ids for a new
    // case. Aligned with the widest default rule window (MSISDN_CHURN_VELOCITY, 1440 min / 24h) so a
    // MEDIUM/LOW pair raised anywhere across the day's rules escalates together.
    private static final Duration ESCALATION_WINDOW = Duration.ofHours(24);

    private final FraudCaseRepository caseRepository;
    private final FraudSignalRepository fraudSignalRepository;
    private final OutboxService outboxService;

    public EscalateFraudCaseCommandHandler(FraudCaseRepository caseRepository,
                                           FraudSignalRepository fraudSignalRepository,
                                           OutboxService outboxService) {
        this.caseRepository = caseRepository;
        this.fraudSignalRepository = fraudSignalRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Void handle(EscalateFraudCaseCommand command) {
        UUID customerId = command.customerId();
        if (customerId == null) {
            // A case is customer-scoped; a signal with no resolvable customer cannot be escalated.
            LOGGER.debug("Skipping escalation for signal id={} with no customer", command.signalId());
            return null;
        }

        Optional<FraudCase> existing = findOpenOrUnderReviewCase(customerId);
        if (existing.isPresent()) {
            FraudCase fraudCase = existing.get();
            fraudCase.attachSignal(command.signalId());
            caseRepository.save(fraudCase);
            LOGGER.info("Attached signal id={} to existing case id={} customerId={}",
                    command.signalId(), fraudCase.getId(), customerId);
            return null;
        }

        Instant windowStart = Instant.now().minus(ESCALATION_WINDOW);
        List<FraudSignal> openWindowSignals = fraudSignalRepository
                .findByCustomerIdAndTriggeredAtGreaterThanEqual(customerId, windowStart);

        boolean shouldOpen = command.severity() == FraudSeverity.HIGH || openWindowSignals.size() > 1;
        if (!shouldOpen) {
            LOGGER.debug("No escalation for signal id={} severity={} openWindowSignals={}",
                    command.signalId(), command.severity(), openWindowSignals.size());
            return null;
        }

        List<UUID> signalIds = new ArrayList<>(
                openWindowSignals.stream().map(FraudSignal::getId).toList());
        if (!signalIds.contains(command.signalId())) {
            signalIds.add(command.signalId());
        }

        FraudSeverity highestSeverity = openWindowSignals.stream()
                .map(FraudSignal::getSeverity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .filter(severity -> severity.ordinal() >= command.severity().ordinal())
                .orElse(command.severity());

        UUID caseId = UUID.randomUUID();
        Instant openedAt = Instant.now();
        FraudCase fraudCase = new FraudCase(
                caseId, customerId, FraudCaseStatus.OPEN, signalIds, openedAt, null, null);
        caseRepository.save(fraudCase);

        outboxService.publish(
                EvaluateRapidSimSwapCommandHandler.OUTBOX_AGGREGATE_TYPE,
                caseId.toString(),
                EVENT_CASE_OPENED,
                new FraudCaseOpenedV1(
                        caseId.toString(),
                        customerId.toString(),
                        signalIds.stream().map(UUID::toString).toList(),
                        openedAt.toEpochMilli(),
                        highestSeverity.name()));
        LOGGER.info("Opened fraud case id={} customerId={} severity={} signals={}",
                caseId, customerId, highestSeverity, signalIds.size());
        return null;
    }

    private Optional<FraudCase> findOpenOrUnderReviewCase(UUID customerId) {
        Optional<FraudCase> open = caseRepository
                .findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(customerId, FraudCaseStatus.OPEN);
        if (open.isPresent()) {
            return open;
        }
        return caseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                customerId, FraudCaseStatus.UNDER_REVIEW);
    }
}
