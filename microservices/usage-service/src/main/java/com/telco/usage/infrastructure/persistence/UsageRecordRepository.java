package com.telco.usage.infrastructure.persistence;

import com.telco.usage.domain.UsageRecord;
import com.telco.usage.domain.UsageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Repository for {@link UsageRecord} (ADR-006). */
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    /** Fast duplicate check using the unique index on cdr_ref (inbox idempotency). */
    boolean existsByCdrRef(String cdrRef);

    /** Paginated usage history for a subscription in a time range. */
    Page<UsageRecord> findBySubscriptionIdAndRecordedAtBetween(
            UUID subscriptionId, Instant from, Instant to, Pageable pageable);

    /**
     * Sums overage quantity for a subscription, usage type, and time window.
     * Used by the aggregation handler to compute billing-relevant overage totals.
     */
    @Query("SELECT SUM(r.quantity) FROM UsageRecord r "
            + "WHERE r.subscriptionId = :subId AND r.type = :type AND r.overage = true "
            + "AND r.recordedAt >= :from AND r.recordedAt < :to")
    Long sumOverageBySubscriptionAndType(
            @Param("subId") UUID subId,
            @Param("type") UsageType type,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
