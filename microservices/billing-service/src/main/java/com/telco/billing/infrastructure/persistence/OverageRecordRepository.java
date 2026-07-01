package com.telco.billing.infrastructure.persistence;

import com.telco.billing.infrastructure.entity.OverageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OverageRecordRepository extends JpaRepository<OverageRecord, UUID> {

    boolean existsBySubscriptionIdAndPeriodStart(UUID subscriptionId, Instant periodStart);

    Optional<OverageRecord> findBySubscriptionIdAndPeriodStart(UUID subscriptionId, Instant periodStart);
}
