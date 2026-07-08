package com.telco.subscription.application.query;

import com.telco.platform.cqrs.Query;
import com.telco.subscription.application.dto.SubscriptionResponse;

import java.util.UUID;

/**
 * Returns a single subscription by its id; a missing id raises {@code ResourceNotFoundException} (-> 404).
 *
 * @param subscriptionId   the subscription's own id
 * @param callerUserId     raw JWT subject of the caller; retained for audit/logging only, no longer
 *                         used for the ownership check (identity-to-customer linkage, ADR-011)
 * @param callerIsAdmin    staff bypass; preserved as-is
 * @param callerCustomerId resolved {@code customerId} claim linked to the caller's identity; null
 *                         when the caller is staff or the identity is not yet linked
 */
public record GetSubscriptionQuery(
        UUID subscriptionId,
        String callerUserId,
        boolean callerIsAdmin,
        String callerCustomerId
) implements Query<SubscriptionResponse> {
}
