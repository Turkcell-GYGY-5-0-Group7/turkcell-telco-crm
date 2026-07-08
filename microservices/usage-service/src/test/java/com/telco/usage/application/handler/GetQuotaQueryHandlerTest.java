package com.telco.usage.application.handler;

import com.telco.usage.application.dto.QuotaResponse;
import com.telco.usage.application.query.GetQuotaQuery;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetQuotaQueryHandlerTest {

    @Mock
    private QuotaRepository quotaRepository;

    @InjectMocks
    private GetQuotaQueryHandler handler;

    private UUID subscriptionId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    private Quota activeQuota(UUID custId) {
        return Quota.create(
                subscriptionId, custId,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                300, 100, 1024);
    }

    private void stubActiveQuota(Quota quota) {
        when(quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        eq(subscriptionId), any(), any()))
                .thenReturn(Optional.of(quota));
    }

    private void stubNoActiveQuota() {
        when(quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        eq(subscriptionId), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void admin_caller_returns_quota_regardless_of_customer_id() {
        stubActiveQuota(activeQuota(customerId));
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, null, true);

        QuotaResponse response = handler.handle(query);

        assertThat(response).isNotNull();
        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
    }

    @Test
    void customer_with_matching_resolved_customer_id_returns_quota() {
        stubActiveQuota(activeQuota(customerId));
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, customerId.toString(), false);

        QuotaResponse response = handler.handle(query);

        assertThat(response).isNotNull();
        assertThat(response.quotaId()).isNotNull();
        assertThat(response.minutesTotal()).isEqualTo(300);
    }

    @Test
    void customer_with_non_matching_resolved_customer_id_throws_access_denied() {
        UUID otherCustomerId = UUID.randomUUID();
        stubActiveQuota(activeQuota(customerId));
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, otherCustomerId.toString(), false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unlinked_subscriber_with_null_resolved_customer_id_throws_access_denied_not_bypass() {
        stubActiveQuota(activeQuota(customerId));
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, null, false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void customer_id_null_on_quota_throws_access_denied_for_non_admin() {
        stubActiveQuota(activeQuota(null));
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, customerId.toString(), false);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void no_active_quota_throws_resource_not_found() {
        stubNoActiveQuota();
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, null, true);

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void response_maps_all_quota_fields_correctly() {
        Quota quota = activeQuota(customerId);
        stubActiveQuota(quota);
        GetQuotaQuery query = new GetQuotaQuery(subscriptionId, customerId.toString(), false);

        QuotaResponse response = handler.handle(query);

        assertThat(response.quotaId()).isEqualTo(quota.getId());
        assertThat(response.minutesTotal()).isEqualTo(quota.getMinutesTotal());
        assertThat(response.smsTotal()).isEqualTo(quota.getSmsTotal());
        assertThat(response.mbTotal()).isEqualTo(quota.getMbTotal());
        assertThat(response.minutesRemaining()).isEqualTo(quota.getMinutesRemaining());
        assertThat(response.smsRemaining()).isEqualTo(quota.getSmsRemaining());
        assertThat(response.mbRemaining()).isEqualTo(quota.getMbRemaining());
    }
}
