package com.telco.subscription.application.query;

import com.telco.platform.cqrs.Query;
import com.telco.subscription.application.dto.SubscriptionResponse;

import java.util.UUID;

/** Returns a single subscription by its id; a missing id raises {@code ResourceNotFoundException} (-> 404). */
public record GetSubscriptionQuery(
        UUID subscriptionId
) implements Query<SubscriptionResponse> {
}
