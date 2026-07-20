package com.telco.fraud.application.handler;

import com.telco.fraud.application.command.ResolveFraudCaseCommand;
import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.application.event.FraudCaseResolvedV1;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Resolves a {@link FraudCase} to a terminal {@code CONFIRMED}/{@code DISMISSED} outcome (Feature
 * 23.3.2). Loads the case (404 via {@link ResourceNotFoundException} if unknown), stamps
 * {@code resolvedAt} and {@code resolvedBy} (the authenticated user id from
 * {@link CurrentUserProvider}), transitions status through the domain {@link FraudCase#resolve}
 * (which rejects an already-resolved case with {@code BusinessRuleException} -> 422), and publishes
 * {@code fraud.case-resolved.v1} through the outbox, atomically with the status update (ADR-009).
 *
 * <p><strong>Detect-and-alert only (ADR-029 Section 5, feature-level hard scope boundary):</strong>
 * this handler's ONLY collaborators are the fraud-owned {@link FraudCaseRepository}, the platform
 * {@link CurrentUserProvider}, and the {@link OutboxService}. There is no RestClient/WebClient/Feign
 * client to subscription-service on any code path reachable from here - resolving a case, even a
 * {@code CONFIRMED} one, NEVER suspends, holds, or otherwise mutates a subscription. Any suspension
 * remains a deliberate, manual, out-of-band agent action against subscription-service.
 */
@Component
public class ResolveFraudCaseCommandHandler
        implements CommandHandler<ResolveFraudCaseCommand, FraudCaseSummaryResponse> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ResolveFraudCaseCommandHandler.class);

    static final String OUTBOX_AGGREGATE_TYPE = "fraud";
    static final String EVENT_CASE_RESOLVED = "fraud.case-resolved.v1";

    private final FraudCaseRepository caseRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OutboxService outboxService;

    public ResolveFraudCaseCommandHandler(FraudCaseRepository caseRepository,
                                          CurrentUserProvider currentUserProvider,
                                          OutboxService outboxService) {
        this.caseRepository = caseRepository;
        this.currentUserProvider = currentUserProvider;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public FraudCaseSummaryResponse handle(ResolveFraudCaseCommand command) {
        FraudCase fraudCase = caseRepository.findById(command.caseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Fraud case not found with id: " + command.caseId(),
                        Map.of("id", command.caseId().toString())));

        String resolvedBy = currentUserProvider.currentUser().userId();
        Instant resolvedAt = Instant.now();

        fraudCase.resolve(command.status(), resolvedBy, resolvedAt);
        caseRepository.save(fraudCase);

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                fraudCase.getId().toString(),
                EVENT_CASE_RESOLVED,
                new FraudCaseResolvedV1(
                        fraudCase.getId().toString(),
                        fraudCase.getCustomerId().toString(),
                        fraudCase.getStatus().name(),
                        resolvedAt.toEpochMilli(),
                        resolvedBy));

        LOGGER.info("Resolved fraud case id={} status={} resolvedBy={} note={}",
                fraudCase.getId(), fraudCase.getStatus(), resolvedBy,
                command.note() != null && !command.note().isBlank());
        return FraudCaseSummaryResponse.from(fraudCase);
    }
}
