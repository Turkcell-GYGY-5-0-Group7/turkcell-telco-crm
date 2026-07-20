package com.telco.subscription.infrastructure;

import com.telco.subscription.domain.SubscriptionAddon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repository for {@link SubscriptionAddon} (FR-09). */
public interface SubscriptionAddonRepository extends JpaRepository<SubscriptionAddon, UUID> {

    List<SubscriptionAddon> findBySubscriptionId(UUID subscriptionId);

    boolean existsByOrderIdAndAddonCode(UUID orderId, String addonCode);
}
