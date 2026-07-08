package com.telco.subscription.application.handler;

import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionsByCustomerQuery;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Returns a customer's subscriptions, paged (FR-15). A customer may hold multiple subscriptions. */
@Component
public class GetSubscriptionsByCustomerQueryHandler
        implements QueryHandler<GetSubscriptionsByCustomerQuery, PageResult<SubscriptionResponse>> {

    private final SubscriptionRepository subscriptionRepository;

    public GetSubscriptionsByCustomerQueryHandler(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public PageResult<SubscriptionResponse> handle(GetSubscriptionsByCustomerQuery query) {
        if (!query.callerIsAdmin()
                && (query.callerCustomerId() == null
                        || !query.callerCustomerId().equals(query.customerId().toString()))) {
            throw new AccessDeniedException("Cannot list subscriptions for another customer");
        }

        PageRequest pageable = PageRequest.of(query.page(), query.size());
        Page<SubscriptionResponse> page = subscriptionRepository
                .findByCustomerId(query.customerId(), pageable)
                .map(SubscriptionResponse::from);

        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
