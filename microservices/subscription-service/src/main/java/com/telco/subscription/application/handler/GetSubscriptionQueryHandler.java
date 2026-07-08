package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionQuery;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

/**
 * Returns a single subscription by id; a missing id raises {@code ResourceNotFoundException} (-> 404).
 *
 * <p>Ownership compares the subscription's {@code customerId} against the caller's <em>resolved</em>
 * {@code customerId} claim (identity-to-customer linkage, ADR-011), not the raw JWT subject - the
 * same fix already applied to {@link GetSubscriptionsByCustomerQueryHandler}. An unlinked caller
 * (null {@code callerCustomerId}) can never own any subscription.
 */
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
                && (query.callerCustomerId() == null
                        || !subscription.getCustomerId().toString().equals(query.callerCustomerId()))) {
            throw new AccessDeniedException("Subscription does not belong to caller");
        }

        return SubscriptionResponse.from(subscription);
    }
}
