package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordOverageCommand;
import com.telco.billing.infrastructure.entity.OverageRecord;
import com.telco.billing.infrastructure.persistence.OverageRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordOverageCommandHandler implements CommandHandler<RecordOverageCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordOverageCommandHandler.class);

    private final OverageRecordRepository overageRepo;

    public RecordOverageCommandHandler(OverageRecordRepository overageRepo) {
        this.overageRepo = overageRepo;
    }

    @Override
    @Transactional
    public Void handle(RecordOverageCommand command) {
        if (overageRepo.existsBySubscriptionIdAndPeriodStart(command.subscriptionId(), command.periodStart())) {
            LOGGER.info("OverageRecord already exists subscriptionId={} periodStart={} — skipping",
                    command.subscriptionId(), command.periodStart());
            return null;
        }

        OverageRecord record = OverageRecord.from(
                command.subscriptionId(), command.periodStart(), command.periodEnd(),
                command.voiceOverageSeconds(), command.smsOverageCount(), command.dataOverageKb(),
                command.aggregatedAt());
        overageRepo.save(record);
        LOGGER.info("Recorded overage subscriptionId={} period=[{},{})",
                command.subscriptionId(), command.periodStart(), command.periodEnd());
        return null;
    }
}
