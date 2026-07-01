package com.telco.subscription.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.subscription.infrastructure.MsisdnPoolRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Domain service for conflict-free MSISDN allocation and release (FR-13).
 *
 * <p>{@link #allocate()} atomically picks a FREE number with a row-level lock
 * ({@code SELECT ... FOR UPDATE SKIP LOCKED}), reserves it, then commits it to ALLOCATED in the same
 * transaction. Because the lock is held to commit, two concurrent allocations never select the same
 * row. {@link #release(String)} returns an ALLOCATED or RESERVED number to FREE.
 *
 * <p>This service holds no transaction of its own; it runs inside the caller's transaction (the
 * mediator {@code TransactionBehavior} wrapping a command handler). The lock and the status flip
 * therefore commit atomically with the rest of the command.
 */
@Service
public class MsisdnAllocationService {

    /** How long a RESERVED hold is valid before an abandoned flow may reclaim the number. */
    private static final Duration RESERVATION_HOLD = Duration.ofMinutes(15);

    private final MsisdnPoolRepository msisdnPoolRepository;

    public MsisdnAllocationService(MsisdnPoolRepository msisdnPoolRepository) {
        this.msisdnPoolRepository = msisdnPoolRepository;
    }

    /**
     * Atomically reserves and allocates the next FREE MSISDN, returning the allocated number.
     *
     * @throws BusinessRuleException when the pool is exhausted (no FREE numbers available).
     */
    public String allocate() {
        MsisdnPool candidate = msisdnPoolRepository.findNextFreeForUpdate()
                .orElseThrow(() -> new BusinessRuleException(
                        "MSISDN pool exhausted: no FREE numbers available for allocation."));

        candidate.reserve(Instant.now().plus(RESERVATION_HOLD));
        candidate.allocate();
        msisdnPoolRepository.save(candidate);
        return candidate.getMsisdn();
    }

    /**
     * Returns an ALLOCATED or RESERVED number to FREE.
     *
     * @throws com.telco.platform.common.exception.ResourceNotFoundException is NOT used here; an
     *         unknown MSISDN is a programming/data error and surfaces as a missing-row failure. A
     *         number already FREE raises {@link BusinessRuleException} from the aggregate.
     */
    public void release(String msisdn) {
        MsisdnPool pool = msisdnPoolRepository.findById(msisdn)
                .orElseThrow(() -> new BusinessRuleException(
                        "Cannot release unknown MSISDN: " + msisdn));
        pool.release();
        msisdnPoolRepository.save(pool);
    }
}
