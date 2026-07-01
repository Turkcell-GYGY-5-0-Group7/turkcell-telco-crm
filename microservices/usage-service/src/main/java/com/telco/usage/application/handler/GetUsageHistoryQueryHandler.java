package com.telco.usage.application.handler;

import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.usage.application.query.GetUsageHistoryQuery;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Returns paginated CDR application history for a subscription within a time range. */
@Component
public class GetUsageHistoryQueryHandler
        implements QueryHandler<GetUsageHistoryQuery, Page<UsageHistoryItem>> {

    private final UsageRecordRepository usageRecordRepository;
    private final QuotaRepository quotaRepository;

    public GetUsageHistoryQueryHandler(UsageRecordRepository usageRecordRepository,
                                       QuotaRepository quotaRepository) {
        this.usageRecordRepository = usageRecordRepository;
        this.quotaRepository = quotaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UsageHistoryItem> handle(GetUsageHistoryQuery query) {
        verifyOwnership(query.principalId(), query.subscriptionId());

        return usageRecordRepository.findBySubscriptionIdAndRecordedAtBetween(
                        query.subscriptionId(), query.from(), query.to(), query.pageable())
                .map(UsageHistoryItem::from);
    }

    private void verifyOwnership(String principalId, java.util.UUID subscriptionId) {
        if (principalId == null) {
            return; // ADMIN - ownership check skipped
        }
        Optional<Quota> anyQuota = quotaRepository.findFirstBySubscriptionId(subscriptionId);
        if (anyQuota.isEmpty()) {
            // No quota means no CDR records exist either, but returning 200/empty would reveal
            // subscription existence. Fail-closed: deny until ownership can be verified.
            throw new AccessDeniedException(
                    "Subscription ownership not yet established for subscriptionId: " + subscriptionId);
        }
        Quota quota = anyQuota.get();
        if (quota.getCustomerId() == null) {
            // customerId not yet set (ProvisionQuotaCommandHandler stub, Sprint 09 pending).
            // Treat unknown owner as deny — fail-closed is the correct security posture.
            throw new AccessDeniedException(
                    "Subscription ownership not yet established for subscriptionId: " + subscriptionId);
        }
        if (!quota.getCustomerId().toString().equals(principalId)) {
            throw new AccessDeniedException(
                    "Not authorized to view history for subscriptionId: " + subscriptionId);
        }
    }
}
