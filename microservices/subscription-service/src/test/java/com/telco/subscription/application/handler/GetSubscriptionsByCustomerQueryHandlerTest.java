package com.telco.subscription.application.handler;

import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ValidationException;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.query.GetSubscriptionsByCustomerQuery;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                        customerId, 0, 20, null, "keycloak-sub", false, customerId.toString()));

        assertThat(result.content()).isEmpty();
    }

    @Test
    void admin_can_list_subscriptions_for_any_customer_even_with_no_linked_customer_id() {
        Page<Subscription> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(subscriptionRepository.findByCustomerId(eq(customerId), any())).thenReturn(page);

        PageResult<SubscriptionResponse> result = handler.handle(
                new GetSubscriptionsByCustomerQuery(customerId, 0, 20, null, "admin-sub", true, null));

        assertThat(result.content()).isEmpty();
    }

    @Test
    void absent_sort_defaults_to_created_at_desc() {
        when(subscriptionRepository.findByCustomerId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        handler.handle(new GetSubscriptionsByCustomerQuery(
                customerId, 0, 20, null, "admin-sub", true, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(subscriptionRepository).findByCustomerId(eq(customerId), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void explicit_sort_is_applied_to_the_repository_call() {
        when(subscriptionRepository.findByCustomerId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        handler.handle(new GetSubscriptionsByCustomerQuery(
                customerId, 0, 20, "activatedAt,asc", "admin-sub", true, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(subscriptionRepository).findByCustomerId(eq(customerId), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "activatedAt"));
    }

    @Test
    void unknown_sort_property_raises_validation_error() {
        assertThatThrownBy(() -> handler.handle(new GetSubscriptionsByCustomerQuery(
                customerId, 0, 20, "msisdn,asc", "admin-sub", true, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void throws_access_denied_when_resolved_customer_id_does_not_match_requested_customer_id() {
        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionsByCustomerQuery(
                        customerId, 0, 20, null, "keycloak-sub", false, UUID.randomUUID().toString())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_access_denied_when_caller_customer_id_is_null_unlinked_subscriber() {
        assertThatThrownBy(() -> handler.handle(
                new GetSubscriptionsByCustomerQuery(customerId, 0, 20, null, "keycloak-sub", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
