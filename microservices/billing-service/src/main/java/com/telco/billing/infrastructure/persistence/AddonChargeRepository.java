package com.telco.billing.infrastructure.persistence;

import com.telco.billing.infrastructure.entity.AddonCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Repository for {@link AddonCharge} (FR-22). */
public interface AddonChargeRepository extends JpaRepository<AddonCharge, UUID> {

    boolean existsByOrderIdAndAddonCode(UUID orderId, String addonCode);

    /** Unbilled charges attached before the period end - the bill-run's addon line source. */
    List<AddonCharge> findBySubscriptionIdAndBilledFalseAndAttachedAtBefore(
            UUID subscriptionId, Instant before);
}
