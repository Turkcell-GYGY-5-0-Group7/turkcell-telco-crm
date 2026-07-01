package com.telco.usage.application.handler;

import com.telco.usage.application.command.AggregateUsageCommand;
import com.telco.usage.application.dto.UsageAggregateResponse;
import com.telco.usage.application.event.UsageAggregatedEvent;
import com.telco.usage.domain.UsageType;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Aggregates overage usage for a billing period and emits {@code usage.aggregated.v1}
 * so downstream billing service can calculate overdue charges (FR-10.4).
 */
@Component
public class AggregateUsageCommandHandler
        implements CommandHandler<AggregateUsageCommand, UsageAggregateResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregateUsageCommandHandler.class);

    // Lowercase outbox routing aggregate type -> `usage.events` topic (billing-service subscribes
    // there); a PascalCase value routes to the wrong topic (event-catalog, ADR-009).
    private static final String OUTBOX_AGGREGATE_TYPE = "usage";
    private static final String EVENT_TYPE = "usage.aggregated.v1";

    private final UsageRecordRepository usageRecordRepository;
    private final OutboxService outboxService;

    public AggregateUsageCommandHandler(UsageRecordRepository usageRecordRepository,
                                        OutboxService outboxService) {
        this.usageRecordRepository = usageRecordRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public UsageAggregateResponse handle(AggregateUsageCommand command) {
        Long voiceOverage = usageRecordRepository.sumOverageBySubscriptionAndType(
                command.subscriptionId(), UsageType.VOICE, command.periodStart(), command.periodEnd());
        Long smsOverage = usageRecordRepository.sumOverageBySubscriptionAndType(
                command.subscriptionId(), UsageType.SMS, command.periodStart(), command.periodEnd());
        Long dataOverage = usageRecordRepository.sumOverageBySubscriptionAndType(
                command.subscriptionId(), UsageType.DATA, command.periodStart(), command.periodEnd());

        long voiceOverageSeconds = voiceOverage != null ? voiceOverage : 0L;
        long smsOverageCount = smsOverage != null ? smsOverage : 0L;
        long dataOverageKb = dataOverage != null ? dataOverage : 0L;

        Instant aggregatedAt = Instant.now();

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE, command.subscriptionId().toString(), EVENT_TYPE,
                new UsageAggregatedEvent(
                        command.subscriptionId().toString(),
                        command.periodStart().toString(),
                        command.periodEnd().toString(),
                        voiceOverageSeconds,
                        smsOverageCount,
                        dataOverageKb,
                        aggregatedAt.toString()));

        LOGGER.info("Usage aggregated subscriptionId={} periodStart={} periodEnd={} "
                        + "voiceOverageSeconds={} smsOverageCount={} dataOverageKb={}",
                command.subscriptionId(), command.periodStart(), command.periodEnd(),
                voiceOverageSeconds, smsOverageCount, dataOverageKb);

        return new UsageAggregateResponse(
                command.subscriptionId(),
                command.periodStart(),
                command.periodEnd(),
                voiceOverageSeconds,
                smsOverageCount,
                dataOverageKb);
    }
}
