package com.telco.usage.infrastructure.persistence;

import com.telco.usage.domain.UsageRecord;
import com.telco.usage.domain.UsageType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Repository for {@link UsageRecord} (ADR-006). */
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    /** Fast duplicate check using the unique index on cdr_ref (inbox idempotency). */
    boolean existsByCdrRef(String cdrRef);

    /** Cursor-based usage history — first page (no cursor). */
    @Query("SELECT r FROM UsageRecord r "
            + "WHERE r.subscriptionId = :subId AND r.recordedAt >= :from AND r.recordedAt <= :to "
            + "ORDER BY r.recordedAt ASC")
    List<UsageRecord> findForCursor(
            @Param("subId") UUID subId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** Cursor-based usage history — subsequent pages (after cursor). */
    @Query("SELECT r FROM UsageRecord r "
            + "WHERE r.subscriptionId = :subId AND r.recordedAt >= :from AND r.recordedAt <= :to "
            + "AND r.recordedAt > :cursor "
            + "ORDER BY r.recordedAt ASC")
    List<UsageRecord> findForCursorAfter(
            @Param("subId") UUID subId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("cursor") Instant cursor,
            Pageable pageable);

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

    /**
     * Distinct subscriptions with any overage usage in the window - the population the scheduled
     * monthly aggregation (FR-20) must emit {@code usage.aggregated.v1} for.
     */
    @Query("SELECT DISTINCT r.subscriptionId FROM UsageRecord r "
            + "WHERE r.overage = true AND r.recordedAt >= :from AND r.recordedAt < :to")
    List<UUID> findSubscriptionIdsWithOverage(
            @Param("from") Instant from,
            @Param("to") Instant to);
}
