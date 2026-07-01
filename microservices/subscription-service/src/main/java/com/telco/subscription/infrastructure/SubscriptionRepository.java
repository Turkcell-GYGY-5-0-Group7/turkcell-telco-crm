package com.telco.subscription.infrastructure;

import com.telco.subscription.domain.Subscription;
import com.telco.subscription.domain.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data JPA repository for {@link Subscription}. */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByCustomerId(UUID customerId);

    Page<Subscription> findByCustomerId(UUID customerId, Pageable pageable);

    List<Subscription> findByCustomerIdAndStatus(UUID customerId, SubscriptionStatus status);
}
