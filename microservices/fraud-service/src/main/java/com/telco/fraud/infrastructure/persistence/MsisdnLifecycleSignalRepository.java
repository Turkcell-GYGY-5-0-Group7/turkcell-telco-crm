package com.telco.fraud.infrastructure.persistence;

import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.domain.MsisdnLifecycleSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the raw ingested event log ({@link MsisdnLifecycleSignal}).
 *
 * <p>Provides the rolling-window lookback queries the rule evaluators (Feature 23.2) run against:
 * MSISDN-scoped for {@code RAPID_SIM_SWAP}, customerId-scoped for {@code MSISDN_CHURN_VELOCITY}, and
 * subscriptionId-scoped for {@code SUSPEND_REACTIVATE_VELOCITY}. Data access only - no
 * rule-evaluation logic lives here (that is Feature 23.2). Each rule reads the corresponding
 * {@link com.telco.fraud.domain.FraudRule#getWindowMinutes()} to derive the {@code windowStart}
 * boundary it passes in.
 */
public interface MsisdnLifecycleSignalRepository extends JpaRepository<MsisdnLifecycleSignal, UUID> {

    /**
     * MSISDN-scoped rolling-window lookback for {@code RAPID_SIM_SWAP}: all events for one MSISDN at
     * or after {@code windowStart}, oldest first, so the evaluator can spot a release then a
     * re-allocation to a different subscription within the window.
     */
    List<MsisdnLifecycleSignal> findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
            String msisdn, Instant windowStart);

    /**
     * customerId-scoped rolling-window lookback for {@code MSISDN_CHURN_VELOCITY}: allocate/release
     * events for one customer at or after {@code windowStart}, to count churn cycles.
     */
    List<MsisdnLifecycleSignal> findByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
            UUID customerId, Collection<MsisdnLifecycleEventType> eventTypes, Instant windowStart);

    /** Count variant of the customerId-scoped churn-velocity lookback. */
    long countByCustomerIdAndEventTypeInAndOccurredAtGreaterThanEqual(
            UUID customerId, Collection<MsisdnLifecycleEventType> eventTypes, Instant windowStart);

    /**
     * subscriptionId-scoped rolling-window lookback for {@code SUSPEND_REACTIVATE_VELOCITY}:
     * suspend/activate events for one subscription at or after {@code windowStart}, to count cycling.
     */
    List<MsisdnLifecycleSignal> findBySubscriptionIdAndEventTypeInAndOccurredAtGreaterThanEqual(
            UUID subscriptionId, Collection<MsisdnLifecycleEventType> eventTypes, Instant windowStart);

    /**
     * Defensive {@code customerId} resolution for {@code msisdn.released.v1} events published before
     * that field existed (ADR-029 Amendment 1): the most recent prior {@code MSISDN_ALLOCATED} row
     * for the same MSISDN at or before the release's {@code occurredAt}. Used by 23.2 to backfill a
     * missing {@code customerId}; a release with no known prior allocation is excluded from the count.
     */
    Optional<MsisdnLifecycleSignal> findFirstByMsisdnAndEventTypeAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
            String msisdn, MsisdnLifecycleEventType eventType, Instant occurredAt);
}
