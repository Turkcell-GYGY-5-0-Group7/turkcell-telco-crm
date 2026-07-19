package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.subscription.application.dto.SubscriptionInternalResponse;
import com.telco.subscription.application.query.GetSubscriptionInternalQuery;
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
 * The internal read is deliberately unguarded (network-perimeter trust, Sprint 24 Feature 24.2):
 * unlike {@link GetSubscriptionQueryHandlerTest}'s guarded sibling there is no ownership dimension
 * to cover - only found (full snapshot mapped) and not-found (404).
 */
@ExtendWith(MockitoExtension.class)
class GetSubscriptionInternalQueryHandlerTest {

    @Mock private SubscriptionRepository subscriptionRepository;

    private GetSubscriptionInternalQueryHandler handler;
    private UUID subscriptionId;
    private UUID customerId;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        handler = new GetSubscriptionInternalQueryHandler(subscriptionRepository);
        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        subscription = Subscription.activate(customerId, "905321234567", "TARIFF-1", 3);
    }

    @Test
    void returns_the_ownership_status_and_tariff_snapshot_when_found() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        SubscriptionInternalResponse response =
                handler.handle(new GetSubscriptionInternalQuery(subscriptionId));

        assertThat(response.id()).isEqualTo(subscription.getId());
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.tariffCode()).isEqualTo("TARIFF-1");
        assertThat(response.tariffVersion()).isEqualTo(3);
        assertThat(response.msisdn()).isEqualTo("905321234567");
    }

    @Test
    void throws_resource_not_found_when_subscription_missing() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetSubscriptionInternalQuery(subscriptionId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(subscriptionId.toString());
    }
}
