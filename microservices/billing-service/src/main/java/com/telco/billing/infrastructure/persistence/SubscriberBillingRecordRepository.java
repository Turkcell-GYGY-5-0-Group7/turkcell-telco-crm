package com.telco.billing.infrastructure.persistence;

import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriberBillingRecordRepository extends JpaRepository<SubscriberBillingRecord, UUID> {

    Optional<SubscriberBillingRecord> findBySubscriptionId(UUID subscriptionId);

    List<SubscriberBillingRecord> findByStatus(String status);
}
