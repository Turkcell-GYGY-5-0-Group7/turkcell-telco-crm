package com.telco.usage.application.handler;

import com.telco.usage.application.command.MeterCdrCommand;
import com.telco.usage.application.event.QuotaExceededEvent;
import com.telco.usage.application.event.QuotaThresholdReachedEvent;
import com.telco.usage.application.event.UsageRecordedEvent;
import com.telco.usage.domain.Quota;
import com.telco.usage.domain.UsageRecord;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Applies a single CDR to the active quota for a subscription (FR-10.1).
 *
 * <p>Flow:
 * <ol>
 *   <li>Duplicate check: if {@code cdrRef} is already recorded, return null (idempotent).</li>
 *   <li>Lock-and-load the active quota with a pessimistic write lock.</li>
 *   <li>If no quota: log a warning and defer (quota provisioning may lag subscription activation).</li>
 *   <li>Call {@link Quota#decrement(com.telco.usage.domain.UsageType, long)} to apply usage.</li>
 *   <li>Persist the usage record and the updated quota.</li>
 *   <li>Publish {@code usage.recorded.v1} and any threshold/exceeded events via the outbox.</li>
 * </ol>
 */
@Component
public class MeterCdrCommandHandler implements CommandHandler<MeterCdrCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeterCdrCommandHandler.class);

    // Lowercase outbox routing aggregate types: Debezium EventRouter routes each outbox row to
    // `<aggregate_type>.events`. Per the event-catalog, usage.* events belong to the `usage` domain
    // (-> usage.events) and quota.* events to the `quota` domain (-> quota.events, which
    // notification-service subscribes to). A PascalCase value routes to the wrong topic (ADR-009).
    private static final String USAGE_AGGREGATE_TYPE = "usage";
    private static final String QUOTA_AGGREGATE_TYPE = "quota";
    private static final String EVENT_USAGE_RECORDED = "usage.recorded.v1";
    private static final String EVENT_THRESHOLD_REACHED = "quota.threshold-reached.v1";
    private static final String EVENT_QUOTA_EXCEEDED = "quota.exceeded.v1";

    private final QuotaRepository quotaRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final OutboxService outboxService;

    public MeterCdrCommandHandler(QuotaRepository quotaRepository,
                                  UsageRecordRepository usageRecordRepository,
                                  OutboxService outboxService) {
        this.quotaRepository = quotaRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Void handle(MeterCdrCommand command) {
        // Idempotency: skip already-processed CDRs.
        if (usageRecordRepository.existsByCdrRef(command.cdrRef())) {
            LOGGER.info("Skipping duplicate CDR cdrRef={} subscriptionId={}",
                    command.cdrRef(), command.subscriptionId());
            return null;
        }

        // Lock the active quota to prevent lost updates under concurrent CDR ingestion.
        Optional<Quota> quotaOpt = quotaRepository.findActiveForUpdateBySubscriptionId(
                command.subscriptionId(), command.occurredAt());

        if (quotaOpt.isEmpty()) {
            LOGGER.warn("No active quota found for subscriptionId={} at occurredAt={} - CDR deferred",
                    command.subscriptionId(), command.occurredAt());
            return null;
        }

        Quota quota = quotaOpt.get();
        Quota.DecrementResult result = quota.decrement(command.usageType(), command.quantity());

        UsageRecord record = UsageRecord.create(
                command.subscriptionId(),
                quota.getId(),
                command.usageType(),
                command.quantity(),
                result.overage(),
                command.cdrRef());

        usageRecordRepository.save(record);
        quotaRepository.save(quota);

        Instant now = Instant.now();

        outboxService.publish(
                USAGE_AGGREGATE_TYPE, quota.getId().toString(), EVENT_USAGE_RECORDED,
                new UsageRecordedEvent(
                        record.getId().toString(),
                        command.subscriptionId().toString(),
                        command.usageType().name(),
                        command.quantity(),
                        result.overage(),
                        now.toString()));

        if (result.thresholdCrossed()) {
            outboxService.publish(
                    QUOTA_AGGREGATE_TYPE, quota.getId().toString(), EVENT_THRESHOLD_REACHED,
                    new QuotaThresholdReachedEvent(
                            command.subscriptionId().toString(),
                            quota.getId().toString(),
                            command.usageType().name(),
                            now.toString()));
            LOGGER.info("Quota threshold reached subscriptionId={} quotaId={} usageType={}",
                    command.subscriptionId(), quota.getId(), command.usageType());
        }

        if (result.exceededCrossed()) {
            outboxService.publish(
                    QUOTA_AGGREGATE_TYPE, quota.getId().toString(), EVENT_QUOTA_EXCEEDED,
                    new QuotaExceededEvent(
                            command.subscriptionId().toString(),
                            quota.getId().toString(),
                            command.usageType().name(),
                            now.toString()));
            LOGGER.info("Quota exceeded subscriptionId={} quotaId={} usageType={}",
                    command.subscriptionId(), quota.getId(), command.usageType());
        }

        LOGGER.info("CDR metered cdrRef={} subscriptionId={} type={} quantity={} overage={}",
                command.cdrRef(), command.subscriptionId(), command.usageType(),
                command.quantity(), result.overage());

        return null;
    }
}
