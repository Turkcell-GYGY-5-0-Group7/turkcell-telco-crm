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

        verifyOwnership(query.principalId(), quota);

        return QuotaResponse.from(quota);
    }

    private void verifyOwnership(String principalId, Quota quota) {
        if (principalId == null) {
            return; // ADMIN - ownership check skipped
        }
        if (quota.getCustomerId() == null) {
            // customerId not yet set (ProvisionQuotaCommandHandler stub, Sprint 09 pending).
            // Treat unknown owner as deny — fail-closed is the correct security posture.
            throw new AccessDeniedException(
                    "Subscription ownership not yet established for subscriptionId: "
                            + quota.getSubscriptionId());
        }
        if (!quota.getCustomerId().toString().equals(principalId)) {
            throw new AccessDeniedException(
                    "Not authorized to view quota for subscriptionId: " + quota.getSubscriptionId());
        }
    }
}
