package com.telco.usage.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.usage.application.dto.QuotaResponse;
import com.telco.usage.application.query.GetQuotaQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * No business logic lives here (ADR-008) — confirms the controller resolves the caller's linked
 * {@code customerId} claim (identity-to-customer linkage, ADR-011) via {@link CurrentUserProvider}
 * and passes it, alongside the staff bypass flag, into the query.
 */
@ExtendWith(MockitoExtension.class)
class UsageControllerTest {

    @Mock private Mediator mediator;
    @Mock private ApiResponseFactory responses;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private Authentication subscriberAuth;
    @Mock private Authentication adminAuth;

    private UsageController controller;

    @BeforeEach
    void setUp() {
        controller = new UsageController(mediator, responses, currentUserProvider);
    }

    @Test
    void getQuota_passes_resolved_customer_id_claim_and_false_admin_flag_for_subscriber() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("keycloak-sub", Set.of("SUBSCRIBER"), null, customerId.toString()));
        QuotaResponse quotaResponse = new QuotaResponse(
                UUID.randomUUID(), subscriptionId, Instant.now(), Instant.now().plusSeconds(3600),
                300, 100, 1024, 300, 100, 1024);
        when(mediator.query(new GetQuotaQuery(subscriptionId, customerId.toString(), false)))
                .thenReturn(quotaResponse);
        when(responses.ok(quotaResponse)).thenReturn(ApiResult.ok(quotaResponse, null));

        ApiResult<QuotaResponse> response = controller.getQuota(subscriptionId, subscriberAuth);

        assertThat(response.data()).isEqualTo(quotaResponse);
    }

    @Test
    void getQuota_passes_null_customer_id_when_subscriber_is_unlinked() {
        UUID subscriptionId = UUID.randomUUID();
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("keycloak-sub", Set.of("SUBSCRIBER"), null, null));
        QuotaResponse quotaResponse = new QuotaResponse(
                UUID.randomUUID(), subscriptionId, Instant.now(), Instant.now().plusSeconds(3600),
                300, 100, 1024, 300, 100, 1024);
        when(mediator.query(new GetQuotaQuery(subscriptionId, null, false))).thenReturn(quotaResponse);
        when(responses.ok(quotaResponse)).thenReturn(ApiResult.ok(quotaResponse, null));

        ApiResult<QuotaResponse> response = controller.getQuota(subscriptionId, subscriberAuth);

        assertThat(response.data()).isEqualTo(quotaResponse);
    }

    @Test
    void getQuota_marks_caller_as_admin_when_role_admin_authority_present() {
        UUID subscriptionId = UUID.randomUUID();
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(adminAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("admin-sub", Set.of("ADMIN"), null, null));
        QuotaResponse quotaResponse = new QuotaResponse(
                UUID.randomUUID(), subscriptionId, Instant.now(), Instant.now().plusSeconds(3600),
                300, 100, 1024, 300, 100, 1024);
        when(mediator.query(new GetQuotaQuery(subscriptionId, null, true))).thenReturn(quotaResponse);
        when(responses.ok(quotaResponse)).thenReturn(ApiResult.ok(quotaResponse, null));

        ApiResult<QuotaResponse> response = controller.getQuota(subscriptionId, adminAuth);

        assertThat(response.data()).isEqualTo(quotaResponse);
    }
}
