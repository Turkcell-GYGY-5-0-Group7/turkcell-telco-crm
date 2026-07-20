package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.ChangeSubscriptionTariffCommand;
import com.telco.subscription.application.event.SubscriptionTariffChangedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeSubscriptionTariffCommandHandlerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private AuditLogWriter auditLogWriter;

    private ChangeSubscriptionTariffCommandHandler handler;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new ChangeSubscriptionTariffCommandHandler(
                subscriptionRepository, outboxService, auditLogWriter);
    }

    private Subscription activeSubscription() {
        return Subscription.activate(customerId, "905320000000", "TARIFF_BASIC", 1);
    }

    @Test
    void changesTariffAuditsAndPublishesTariffChangedEvent() {
        Subscription subscription = activeSubscription();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        UUID result = handler.handle(new ChangeSubscriptionTariffCommand(
                subscription.getId(), orderId, customerId, "TARIFF_PREMIUM", "msg-1"));

        assertThat(result).isEqualTo(subscription.getId());
        assertThat(subscription.getTariffCode()).isEqualTo("TARIFF_PREMIUM");
        verify(subscriptionRepository).save(subscription);
        verify(auditLogWriter).log(eq("SUBSCRIPTION_TARIFF_CHANGED"), eq("Subscription"),
                eq(subscription.getId().toString()), any());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("subscription"), eq(subscription.getId().toString()),
                eq("subscription.tariff-changed.v1"), payloadCaptor.capture());
        SubscriptionTariffChangedV1 event = (SubscriptionTariffChangedV1) payloadCaptor.getValue();
        assertThat(event.oldTariffCode()).isEqualTo("TARIFF_BASIC");
        assertThat(event.newTariffCode()).isEqualTo("TARIFF_PREMIUM");
        assertThat(event.orderId()).isEqualTo(orderId.toString());
        assertThat(event.customerId()).isEqualTo(customerId.toString());
    }

    @Test
    void unknownSubscriptionThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(subscriptionRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ChangeSubscriptionTariffCommand(
                missing, orderId, customerId, "TARIFF_PREMIUM", "msg-1")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void subscriptionOwnedByAnotherCustomerIsRejected() {
        Subscription subscription = activeSubscription();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> handler.handle(new ChangeSubscriptionTariffCommand(
                subscription.getId(), orderId, UUID.randomUUID(), "TARIFF_PREMIUM", "msg-1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong");
        assertThat(subscription.getTariffCode()).isEqualTo("TARIFF_BASIC");
        verify(subscriptionRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void suspendedSubscriptionCannotChangeTariff() {
        Subscription subscription = activeSubscription();
        subscription.suspend();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> handler.handle(new ChangeSubscriptionTariffCommand(
                subscription.getId(), orderId, customerId, "TARIFF_PREMIUM", "msg-1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only ACTIVE");
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
