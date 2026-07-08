package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionQuery;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Ownership check compares the resolved {@code customerId} claim (identity-to-customer linkage,
 * ADR-011) against the subscription's own {@code customerId}, not the raw JWT subject - a real,
 * previously-undiscovered gap in this single-subscription-by-id read found and fixed during Feature
 * 14.4's end-to-end verification (the sibling by-customer list query had already been fixed, this one
 * had not, and had no test coverage at all before this).
 */
@ExtendWith(MockitoExtension.class)
class GetSubscriptionQueryHandlerTest {

    @Mock private SubscriptionRepository subscriptionRepository;

    private GetSubscriptionQueryHandler handler;
    private UUID subscriptionId;
    private UUID customerId;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        handler = new GetSubscriptionQueryHandler(subscriptionRepository);
        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        subscription = Subscription.activate(customerId, "905321234567", "TARIFF-1", 1);
    }

    @Test
    void returns_subscription_when_resolved_customer_id_matches_owner() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        SubscriptionResponse response = handler.handle(
                new GetSubscriptionQuery(subscriptionId, "keycloak-sub", false, customerId.toString()));

        assertThat(response.customerId()).isEqualTo(customerId);
    }

    @Test
    void admin_can_fetch_any_subscription_even_with_no_linked_customer_id() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        SubscriptionResponse response = handler.handle(
                new GetSubscriptionQuery(subscriptionId, "admin-sub", true, null));

        assertThat(response.customerId()).isEqualTo(customerId);
    }

    @Test
    void throws_access_denied_when_resolved_customer_id_does_not_match_owner() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionQuery(subscriptionId, "keycloak-sub", false, UUID.randomUUID().toString())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_access_denied_when_caller_customer_id_is_null_unlinked_subscriber() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionQuery(subscriptionId, "keycloak-sub", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_resource_not_found_when_subscription_missing() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionQuery(subscriptionId, "keycloak-sub", true, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
