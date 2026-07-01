package com.telco.subscription.application.query;

import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;
import com.telco.subscription.application.dto.SubscriptionResponse;

import java.util.UUID;

/** Returns a paginated list of a customer's subscriptions (FR-15). */
public record GetSubscriptionsByCustomerQuery(
        UUID customerId,
        int page,
        int size,
        String callerUserId,
        boolean callerIsAdmin
) implements Query<PageResult<SubscriptionResponse>> {
}
