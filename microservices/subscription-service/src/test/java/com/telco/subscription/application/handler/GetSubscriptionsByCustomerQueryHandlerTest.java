package com.telco.subscription.application.handler;

import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionsByCustomerQuery;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ownership check now compares the resolved {@code customerId} claim (identity-to-customer
 * linkage, ADR-011) against the requested {@code customerId}, not the raw JWT subject.
 */
@ExtendWith(MockitoExtension.class)
class GetSubscriptionsByCustomerQueryHandlerTest {

    @Mock private SubscriptionRepository subscriptionRepository;

    private GetSubscriptionsByCustomerQueryHandler handler;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        handler = new GetSubscriptionsByCustomerQueryHandler(subscriptionRepository);
        customerId = UUID.randomUUID();
    }

    @Test
    void returns_page_when_resolved_customer_id_matches_requested_customer_id() {
        Page<Subscription> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(subscriptionRepository.findByCustomerId(eq(customerId), any())).thenReturn(page);

        PageResult<SubscriptionResponse> result = handler.handle(
                new GetSubscriptionsByCustomerQuery(
                        customerId, 0, 20, "keycloak-sub", false, customerId.toString()));

        assertThat(result.content()).isEmpty();
    }

    @Test
    void admin_can_list_subscriptions_for_any_customer_even_with_no_linked_customer_id() {
        Page<Subscription> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(subscriptionRepository.findByCustomerId(eq(customerId), any())).thenReturn(page);

        PageResult<SubscriptionResponse> result = handler.handle(
                new GetSubscriptionsByCustomerQuery(customerId, 0, 20, "admin-sub", true, null));

        assertThat(result.content()).isEmpty();
    }

    @Test
    void throws_access_denied_when_resolved_customer_id_does_not_match_requested_customer_id() {
        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionsByCustomerQuery(
                        customerId, 0, 20, "keycloak-sub", false, UUID.randomUUID().toString())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_access_denied_when_caller_customer_id_is_null_unlinked_subscriber() {
        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionsByCustomerQuery(customerId, 0, 20, "keycloak-sub", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
