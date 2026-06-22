package com.telco.platform.starter.outbox;

import com.telco.platform.outbox.OutboxStatus;
import com.telco.platform.outbox.OutboxStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodically counts outbox rows still in {@code NEW} beyond a staleness threshold and logs a
 * warning. A growing count signals that Debezium (the primary delivery per ADR-005) is lagging or
 * down. This is monitoring only; it does not change row status. Enabled by default and configured
 * via {@code telco.platform.outbox.sweeper.*}.
 */
public final class OutboxStuckSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxStuckSweeper.class);

    private final OutboxStore store;
    private final Duration staleThreshold;

    public OutboxStuckSweeper(OutboxStore store, Duration staleThreshold) {
        this.store = store;
        this.staleThreshold = staleThreshold;
    }

    @Scheduled(fixedDelayString = "${telco.platform.outbox.sweeper.interval-ms:60000}")
    public void sweep() {
        int stuck = store.countByStatusOlderThan(OutboxStatus.NEW, Instant.now().minus(staleThreshold));
        if (stuck > 0) {
            LOGGER.warn("Outbox: {} row(s) still NEW beyond {}s (Debezium may be lagging or down)",
                    stuck, staleThreshold.toSeconds());
        }
    }
}
