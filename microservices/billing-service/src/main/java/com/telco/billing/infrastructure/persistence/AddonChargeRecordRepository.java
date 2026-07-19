package com.telco.billing.infrastructure.persistence;

import com.telco.billing.infrastructure.entity.AddonChargeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repository for the {@link AddonChargeRecord} read model (Sprint 24 Feature 24.3). */
public interface AddonChargeRecordRepository extends JpaRepository<AddonChargeRecord, UUID> {

    /** Unbilled charges the next bill run must invoice for this subscription (design-note D3). */
    List<AddonChargeRecord> findBySubscriptionIdAndBilledFalse(UUID subscriptionId);
}
