package com.telco.platform.starter.outbox;

import com.telco.platform.outbox.OutboxRecord;
import com.telco.platform.outbox.OutboxStatus;
import com.telco.platform.outbox.OutboxStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Optional polling relay that marks {@code NEW} outbox rows as published.
 *
 * <p>Disabled by default. Debezium change-data-capture is the primary delivery mechanism per
 * ADR-005; this relay is a fallback for environments without CDC. It does not itself produce to
 * Kafka - it is a hook point. Enable with {@code telco.platform.outbox.relay.enabled=true}.
 */
public final class OutboxRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxStore store;
    private final int batchSize;

    public OutboxRelayScheduler(OutboxStore store, int batchSize) {
        this.store = store;
        this.batchSize = batchSize;
    }

    /** Polls for new rows and marks them published. Interval configurable via standard scheduling. */
    @Scheduled(fixedDelayString = "${telco.platform.outbox.relay.poll-interval-ms:5000}")
    public void relay() {
        List<OutboxRecord> pending = store.findByStatus(OutboxStatus.NEW, batchSize);
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxRecord record : pending) {
            try {
                store.markPublished(record.id());
            } catch (RuntimeException ex) {
                LOGGER.warn("Outbox relay failed to mark record {} published: {}", record.id(), ex.getMessage());
                store.markFailed(record.id(), ex.getMessage());
            }
        }
        LOGGER.debug("Outbox relay processed {} record(s)", pending.size());
    }
}
