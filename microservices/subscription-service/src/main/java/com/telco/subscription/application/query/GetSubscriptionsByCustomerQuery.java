package com.telco.subscription.application.query;

import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;
import com.telco.subscription.application.dto.SubscriptionResponse;

import java.util.UUID;

/**
 * Returns a paginated list of a customer's subscriptions (FR-15).
 *
 * @param sort               optional {@code field,asc|desc} sort expression; null/blank means
 *                           {@code createdAt,desc}
 * @param callerUserId       raw JWT subject of the caller; retained for audit/logging only, no
 *                           longer used for the ownership check (identity-to-customer linkage,
 *                           ADR-011)
 * @param callerCustomerId   resolved {@code customerId} claim linked to the caller's identity;
 *                           null when the caller is staff or the identity is not yet linked
 * @param callerIsAdmin      staff bypass; preserved as-is
 */
public record GetSubscriptionsByCustomerQuery(
        UUID customerId,
        int page,
        int size,
        String sort,
        String callerUserId,
        boolean callerIsAdmin,
        String callerCustomerId
) implements Query<PageResult<SubscriptionResponse>> {
}
