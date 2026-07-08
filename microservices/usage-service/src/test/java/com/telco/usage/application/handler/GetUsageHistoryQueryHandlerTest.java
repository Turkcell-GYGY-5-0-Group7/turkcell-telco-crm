package com.telco.usage.application.handler;

import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.usage.application.query.GetUsageHistoryQuery;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.common.api.CursorPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUsageHistoryQueryHandlerTest {

    @Mock
    private UsageRecordRepository usageRecordRepository;

    @Mock
    private QuotaRepository quotaRepository;

    @InjectMocks
    private GetUsageHistoryQueryHandler handler;

    private UUID subscriptionId;
    private UUID customerId;
    private Instant from;
    private Instant to;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        from = Instant.parse("2026-06-01T00:00:00Z");
        to = Instant.parse("2026-07-01T00:00:00Z");
    }

    private Quota quotaFor(UUID custId) {
        return Quota.create(subscriptionId, custId,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                300, 100, 1024);
    }

    private void stubEmptyHistory() {
        when(usageRecordRepository.findForCursor(
                eq(subscriptionId), any(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void admin_caller_returns_history_without_ownership_check() {
        stubEmptyHistory();
        GetUsageHistoryQuery query = new GetUsageHistoryQuery(
                subscriptionId, from, to, null, 50, null, true);

        CursorPage<UsageHistoryItem> result = handler.handle(query);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void customer_with_matching_resolved_customer_id_returns_history() {
        when(quotaRepository.findFirstBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(quotaFor(customerId)));
        stubEmptyHistory();
        GetUsageHistoryQuery query = new GetUsageHistoryQuery(
                subscriptionId, from, to, null, 50, customerId.toString(), false);

        CursorPage<UsageHistoryItem> result = handler.handle(query);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEmpty();
    }

    @Test
    void unlinked_subscriber_with_null_resolved_customer_id_throws_access_denied_not_bypass() {
        GetUsageHistoryQuery query = new GetUsageHistoryQuery(
                subscriptionId, from, to, null, 50, null, false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void no_quota_found_for_customer_throws_access_denied_fail_closed() {
        when(quotaRepository.findFirstBySubscriptionId(subscriptionId))
                .thenReturn(Optional.empty());
        GetUsageHistoryQuery query = new GetUsageHistoryQuery(
                subscriptionId, from, to, null, 50, customerId.toString(), false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void customer_id_null_on_quota_throws_access_denied() {
        when(quotaRepository.findFirstBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(quotaFor(null)));
        GetUsageHistoryQuery query = new GetUsageHistoryQuery(
                subscriptionId, from, to, null, 50, customerId.toString(), false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void customer_with_non_matching_resolved_customer_id_throws_access_denied() {
        UUID otherCustomer = UUID.randomUUID();
        when(quotaRepository.findFirstBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(quotaFor(customerId)));
        GetUsageHistoryQuery query = new GetUsageHistoryQuery(
                subscriptionId, from, to, null, 50, otherCustomer.toString(), false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }
}
