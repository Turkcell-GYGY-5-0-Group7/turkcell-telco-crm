package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionQuery;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

/** Returns a single subscription by id; a missing id raises {@code ResourceNotFoundException} (-> 404). */
@Component
public class GetSubscriptionQueryHandler
        implements QueryHandler<GetSubscriptionQuery, SubscriptionResponse> {

    private final SubscriptionRepository subscriptionRepository;

    public GetSubscriptionQueryHandler(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public SubscriptionResponse handle(GetSubscriptionQuery query) {
        Subscription subscription = subscriptionRepository.findById(query.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + query.subscriptionId()));

        if (!query.callerIsAdmin()
                && !subscription.getCustomerId().toString().equals(query.callerUserId())) {
            throw new AccessDeniedException("Subscription does not belong to caller");
        }

        return SubscriptionResponse.from(subscription);
    }
}
