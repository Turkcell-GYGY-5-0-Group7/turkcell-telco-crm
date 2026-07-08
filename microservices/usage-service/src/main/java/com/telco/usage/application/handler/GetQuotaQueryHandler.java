package com.telco.usage.application.handler;

import com.telco.usage.application.dto.QuotaResponse;
import com.telco.usage.application.query.GetQuotaQuery;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Returns the currently active quota for a subscription, or 404 if none exists. */
@Component
public class GetQuotaQueryHandler implements QueryHandler<GetQuotaQuery, QuotaResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetQuotaQueryHandler.class);

    private final QuotaRepository quotaRepository;

    public GetQuotaQueryHandler(QuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaResponse handle(GetQuotaQuery query) {
        Instant now = Instant.now();
        Quota quota = quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        query.subscriptionId(), now, now)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active quota found for subscriptionId: " + query.subscriptionId()));

        verifyOwnership(query.callerCustomerId(), query.callerIsAdmin(), quota);

        return QuotaResponse.from(quota);
    }

    private void verifyOwnership(String callerCustomerId, boolean callerIsAdmin, Quota quota) {
        if (callerIsAdmin) {
            return; // staff bypass
        }
        if (callerCustomerId == null) {
            // Caller has no linked customerId (unlinked subscriber, or identity-to-customer
            // linkage, ADR-011, not yet established for this identity). Never treat a null
            // resolved customerId as a bypass — fail-closed.
            throw new AccessDeniedException(
                    "Caller identity is not linked to a customer for subscriptionId: "
                            + quota.getSubscriptionId());
        }
        if (quota.getCustomerId() == null) {
            // customerId not yet set (ProvisionQuotaCommandHandler stub, Sprint 09 pending).
            // Treat unknown owner as deny — fail-closed is the correct security posture.
            throw new AccessDeniedException(
                    "Subscription ownership not yet established for subscriptionId: "
                            + quota.getSubscriptionId());
        }
        if (!callerCustomerId.equals(quota.getCustomerId().toString())) {
            throw new AccessDeniedException(
                    "Not authorized to view quota for subscriptionId: " + quota.getSubscriptionId());
        }
    }
}
