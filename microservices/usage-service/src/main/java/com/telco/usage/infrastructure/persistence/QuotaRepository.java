package com.telco.usage.infrastructure.persistence;

import com.telco.usage.domain.Quota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Repository for the {@link Quota} aggregate (ADR-006). */
public interface QuotaRepository extends JpaRepository<Quota, UUID> {

    /**
     * Finds the active quota for a subscription at a given point in time.
     * Used for non-locking reads (e.g., query handlers).
     */
    Optional<Quota> findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
            UUID subscriptionId, Instant periodStart, Instant periodEnd);

    /** Returns any quota for a subscription; used for ownership verification on read paths. */
    Optional<Quota> findFirstBySubscriptionId(UUID subscriptionId);

    /** Existence check used by {@code ProvisionQuotaCommandHandler} for idempotency. */
    boolean existsBySubscriptionIdAndPeriodStart(UUID subscriptionId, Instant periodStart);

    /**
     * Locks and loads the active quota for metering.
     * The pessimistic write lock prevents concurrent CDR processing from creating
     * a lost-update on the remaining counters (write-heavy CDR ingestion path).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quota q WHERE q.subscriptionId = :subscriptionId "
            + "AND q.periodStart <= :at AND q.periodEnd > :at")
    Optional<Quota> findActiveForUpdateBySubscriptionId(
            @Param("subscriptionId") UUID subscriptionId, @Param("at") Instant at);
}
