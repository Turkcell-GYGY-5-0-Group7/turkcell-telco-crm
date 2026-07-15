package com.telco.subscription.infrastructure;

import com.telco.subscription.domain.MsisdnPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA repository for the MSISDN pool ({@link MsisdnPool}). */
public interface MsisdnPoolRepository extends JpaRepository<MsisdnPool, String> {

    /**
     * Atomically picks one FREE number for allocation. {@code FOR UPDATE SKIP LOCKED} takes a
     * row-level write lock and skips rows already locked by a concurrent transaction, so two parallel
     * allocations never select the same MSISDN (FR-13). The lock is held until the surrounding
     * transaction (the mediator {@code TransactionBehavior}) commits or rolls back.
     */
    @Query(value = """
            SELECT * FROM msisdn_pool
            WHERE status = 'FREE'
            ORDER BY msisdn
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<MsisdnPool> findNextFreeForUpdate();

    long countByStatus(com.telco.subscription.domain.MsisdnStatus status);

    /** RESERVED holds whose {@code reservedUntil} has elapsed (feature 17.3, ADR-024). */
    List<MsisdnPool> findByStatusAndReservedUntilBefore(
            com.telco.subscription.domain.MsisdnStatus status, Instant cutoff);
}
