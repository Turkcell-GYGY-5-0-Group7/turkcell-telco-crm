package com.telco.subscription.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionsByCustomerQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * No business logic lives here (ADR-008) — confirms the controller resolves the caller's linked
 * {@code customerId} claim (identity-to-customer linkage, ADR-011) via {@link CurrentUserProvider}
 * and passes it into the query alongside the raw caller id and admin flag.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock private Mediator mediator;
    @Mock private ApiResponseFactory responses;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private Authentication subscriberAuth;

    private SubscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new SubscriptionController(mediator, responses, currentUserProvider);
    }

    @Test
    void getByCustomer_passes_resolved_customer_id_claim_into_the_query() {
        UUID customerId = UUID.randomUUID();
        when(subscriberAuth.getName()).thenReturn("keycloak-sub");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("keycloak-sub", Set.of("SUBSCRIBER"), null, customerId.toString()));
        PageResult<SubscriptionResponse> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        when(mediator.query(new GetSubscriptionsByCustomerQuery(
                        customerId, 0, 20, null, "keycloak-sub", false, customerId.toString())))
                .thenReturn(page);
        when(responses.ok(page)).thenReturn(ApiResult.ok(page, null));

        ApiResult<PageResult<SubscriptionResponse>> response =
                controller.getByCustomer(subscriberAuth, customerId, 0, 20, null);

        assertThat(response.data()).isEqualTo(page);
    }

    @Test
    void getByCustomer_passes_null_customer_id_when_caller_is_unlinked() {
        UUID customerId = UUID.randomUUID();
        when(subscriberAuth.getName()).thenReturn("keycloak-sub");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("keycloak-sub", Set.of("SUBSCRIBER"), null, null));
        PageResult<SubscriptionResponse> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        when(mediator.query(new GetSubscriptionsByCustomerQuery(
                        customerId, 0, 20, null, "keycloak-sub", false, null)))
                .thenReturn(page);
        when(responses.ok(page)).thenReturn(ApiResult.ok(page, null));

        ApiResult<PageResult<SubscriptionResponse>> response =
                controller.getByCustomer(subscriberAuth, customerId, 0, 20, null);

        assertThat(response.data()).isEqualTo(page);
    }
}
