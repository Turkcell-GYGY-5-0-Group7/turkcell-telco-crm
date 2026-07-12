package com.telco.subscription.infrastructure.scheduler;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockErrorCode;
import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.ExpireMsisdnReservationsCommand;
import com.telco.subscription.application.command.ExpireMsisdnReservationsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reaps expired {@code RESERVED} MSISDN holds back to {@code FREE} (feature 17.3, FR-13, ADR-024).
 *
 * <p>{@code @Scheduled} runs identically on every replica once subscription-service scales out
 * (Sprint 15 HPA); the sweep is guarded by a {@link DistributedLock} using an explicit
 * (non-watchdog-managed) lease - ADR-024 Section 4's guidance for a bounded, per-tick sweep - so
 * exactly one replica performs it per tick. A replica that loses the race for the lock simply skips
 * this tick and lets the next scheduled tick retry: no error, no duplicate sweep.
 *
 * <p>{@code telco.subscription.msisdn-reaper.enabled} (default true) gates this bean entirely - off
 * in the shared "test" profile so this doesn't fire against every unrelated Spring-context test's
 * database; a test that wants the bean (e.g. a concurrency IT calling {@link #tick()} directly)
 * re-enables it explicitly.
 */
@Component
@ConditionalOnProperty(prefix = "telco.subscription.msisdn-reaper", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MsisdnReservationExpiryReaper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MsisdnReservationExpiryReaper.class);
    private static final String LOCK_KEY = "subscription-service:msisdn-reaper";

    private final Mediator mediator;
    private final DistributedLock distributedLock;
    private final Duration lockLease;

    public MsisdnReservationExpiryReaper(
            Mediator mediator,
            DistributedLock distributedLock,
            @Value("${telco.subscription.msisdn-reaper.lock-lease-ms:30000}") long lockLeaseMs) {
        this.mediator = mediator;
        this.distributedLock = distributedLock;
        this.lockLease = Duration.ofMillis(lockLeaseMs);
    }

    @Scheduled(fixedDelayString = "${telco.subscription.msisdn-reaper.interval-ms:60000}")
    public void sweep() {
        tick();
    }

    /**
     * Runs one lock-guarded sweep attempt.
     *
     * @return the number of reservations released, or {@code -1} if this replica lost the race for
     *         the lock this tick (distinguishable from a genuine zero-work tick)
     */
    int tick() {
        try {
            return distributedLock.withLock(LOCK_KEY, lockLease, this::runSweep);
        } catch (DependencyFailureException e) {
            if (e.code() == LockErrorCode.LOCK_ACQUISITION_FAILED) {
                LOGGER.debug("MSISDN reaper: another replica already owns this tick - skipping");
                return -1;
            }
            throw e;
        }
    }

    private int runSweep() {
        ExpireMsisdnReservationsResult result = mediator.send(new ExpireMsisdnReservationsCommand());
        if (result.releasedCount() > 0) {
            LOGGER.info("MSISDN reaper released {} expired reservation(s) back to FREE", result.releasedCount());
        }
        return result.releasedCount();
    }
}
