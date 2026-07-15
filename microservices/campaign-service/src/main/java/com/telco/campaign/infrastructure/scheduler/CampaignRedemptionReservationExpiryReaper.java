package com.telco.campaign.infrastructure.scheduler;

import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsCommand;
import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockErrorCode;
import com.telco.platform.mediator.Mediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reaps expired {@code RESERVED} {@code CampaignRedemption} holds back to {@code RELEASED} (Feature
 * 21.4, ADR-027 Section 4 ratification amendment, ADR-024). Mirrors subscription-service's
 * {@code MsisdnReservationExpiryReaper} (Feature 17.3) exactly.
 *
 * <p>{@code @Scheduled} runs identically on every replica once campaign-service scales out; the sweep
 * is guarded by a {@link DistributedLock} using an explicit (non-watchdog-managed) lease (ADR-024
 * Section 4's guidance for a bounded, per-tick sweep) so exactly one replica performs it per tick. A
 * replica that loses the race for the lock simply skips this tick and lets the next scheduled tick
 * retry: no error, no duplicate sweep.
 *
 * <p>{@code telco.campaign.redemption-reaper.enabled} (default true) gates this bean entirely - off in
 * the shared "test" profile so this doesn't fire against every unrelated Spring-context test's
 * database; a test that wants the bean re-enables it explicitly. The first tick is delayed by
 * {@code telco.campaign.redemption-reaper.initial-delay-ms} (default 60s, same as the tick interval)
 * rather than firing immediately at context startup, so the sweep never runs while the service (and
 * its peers) are still warming up.
 */
@Component
@ConditionalOnProperty(prefix = "telco.campaign.redemption-reaper", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class CampaignRedemptionReservationExpiryReaper {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CampaignRedemptionReservationExpiryReaper.class);
    private static final String LOCK_KEY = "campaign-service:redemption-reaper";

    private final Mediator mediator;
    private final DistributedLock distributedLock;
    private final Duration lockLease;

    public CampaignRedemptionReservationExpiryReaper(
            Mediator mediator,
            DistributedLock distributedLock,
            @Value("${telco.campaign.redemption-reaper.lock-lease-ms:30000}") long lockLeaseMs) {
        this.mediator = mediator;
        this.distributedLock = distributedLock;
        this.lockLease = Duration.ofMillis(lockLeaseMs);
    }

    @Scheduled(
            fixedDelayString = "${telco.campaign.redemption-reaper.interval-ms:60000}",
            initialDelayString = "${telco.campaign.redemption-reaper.initial-delay-ms:60000}")
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
                LOGGER.debug("Campaign redemption reaper: another replica already owns this tick - skipping");
                return -1;
            }
            throw e;
        }
    }

    private int runSweep() {
        ExpireCampaignRedemptionReservationsResult result =
                mediator.send(new ExpireCampaignRedemptionReservationsCommand());
        if (result.releasedCount() > 0) {
            LOGGER.info("Campaign redemption reaper released {} expired reservation(s) back to RELEASED",
                    result.releasedCount());
        }
        return result.releasedCount();
    }
}
