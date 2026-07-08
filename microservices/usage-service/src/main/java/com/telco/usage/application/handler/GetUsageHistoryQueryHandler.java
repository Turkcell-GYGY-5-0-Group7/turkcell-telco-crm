package com.telco.usage.application.handler;

import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.usage.application.query.GetUsageHistoryQuery;
import com.telco.usage.domain.Quota;
import com.telco.usage.domain.UsageRecord;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.common.api.CursorPage;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Returns cursor-paginated CDR history for a subscription within a time range (ADR-015). */
@Component
public class GetUsageHistoryQueryHandler
        implements QueryHandler<GetUsageHistoryQuery, CursorPage<UsageHistoryItem>> {

    private final UsageRecordRepository usageRecordRepository;
    private final QuotaRepository quotaRepository;

    public GetUsageHistoryQueryHandler(UsageRecordRepository usageRecordRepository,
                                       QuotaRepository quotaRepository) {
        this.usageRecordRepository = usageRecordRepository;
        this.quotaRepository = quotaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<UsageHistoryItem> handle(GetUsageHistoryQuery query) {
        verifyOwnership(query.callerCustomerId(), query.callerIsAdmin(), query.subscriptionId());

        // Fetch one extra record to determine if there is a next page.
        List<UsageRecord> rows;
        if (query.cursor() == null) {
            rows = usageRecordRepository.findForCursor(
                    query.subscriptionId(), query.from(), query.to(),
                    PageRequest.ofSize(query.limit() + 1));
        } else {
            rows = usageRecordRepository.findForCursorAfter(
                    query.subscriptionId(), query.from(), query.to(), Instant.parse(query.cursor()),
                    PageRequest.ofSize(query.limit() + 1));
        }

        boolean hasNext = rows.size() > query.limit();
        List<UsageRecord> page = hasNext ? rows.subList(0, query.limit()) : rows;

        String nextCursor = hasNext
                ? page.get(page.size() - 1).getRecordedAt().toString()
                : null;

        List<UsageHistoryItem> content = page.stream().map(UsageHistoryItem::from).toList();
        return new CursorPage<>(content, nextCursor, hasNext, query.limit());
    }

    private void verifyOwnership(String callerCustomerId, boolean callerIsAdmin,
                                  java.util.UUID subscriptionId) {
        if (callerIsAdmin) {
            return; // staff bypass
        }
        if (callerCustomerId == null) {
            // Caller has no linked customerId (unlinked subscriber, or identity-to-customer
            // linkage, ADR-011, not yet established for this identity). Never treat a null
            // resolved customerId as a bypass — fail-closed.
            throw new AccessDeniedException(
                    "Caller identity is not linked to a customer for subscriptionId: " + subscriptionId);
        }
        Optional<Quota> anyQuota = quotaRepository.findFirstBySubscriptionId(subscriptionId);
        if (anyQuota.isEmpty()) {
            throw new AccessDeniedException(
                    "Subscription ownership not yet established for subscriptionId: " + subscriptionId);
        }
        Quota quota = anyQuota.get();
        if (quota.getCustomerId() == null
                || !quota.getCustomerId().toString().equals(callerCustomerId)) {
            throw new AccessDeniedException(
                    "Not authorized to view history for subscriptionId: " + subscriptionId);
        }
    }
}
