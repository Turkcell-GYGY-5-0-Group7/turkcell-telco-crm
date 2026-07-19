package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.subscription.application.dto.SubscriptionInternalResponse;
import com.telco.subscription.application.query.GetSubscriptionInternalQuery;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

/**
 * System read of a single subscription by its id, with NO ownership guard. Distinct from
 * {@link GetSubscriptionQueryHandler} (which enforces the resolved-customerId ownership check,
 * ADR-011): this path is exclusively for the trusted internal endpoint order-service calls when
 * validating ADDON/PLAN_CHANGE order targets (Sprint 24 Feature 24.2) and must never weaken the
 * guarded path - mirroring order-service's own {@code GetOrderInternalQueryHandler} split
 * (tech-lead ruling 1a). A missing id raises {@link ResourceNotFoundException} (-&gt; 404).
 */
@Component
public class GetSubscriptionInternalQueryHandler
        implements QueryHandler<GetSubscriptionInternalQuery, SubscriptionInternalResponse> {

    private final SubscriptionRepository subscriptionRepository;

    public GetSubscriptionInternalQueryHandler(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public SubscriptionInternalResponse handle(GetSubscriptionInternalQuery query) {
        Subscription subscription = subscriptionRepository.findById(query.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + query.subscriptionId()));
        return SubscriptionInternalResponse.from(subscription);
    }
}
