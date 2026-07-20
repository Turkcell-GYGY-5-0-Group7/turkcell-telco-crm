package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.AttachAddonCommand;
import com.telco.subscription.application.event.SubscriptionAddonAttachedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.domain.SubscriptionAddon;
import com.telco.subscription.infrastructure.SubscriptionAddonRepository;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class AttachAddonCommandHandlerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionAddonRepository subscriptionAddonRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private AuditLogWriter auditLogWriter;

    private AttachAddonCommandHandler handler;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new AttachAddonCommandHandler(subscriptionRepository, subscriptionAddonRepository,
                outboxService, auditLogWriter);
    }

    private Subscription activeSubscription() {
        return Subscription.activate(customerId, "905320000000", "TARIFF_BASIC", 1);
    }

    private AttachAddonCommand command(UUID subscriptionId, UUID customer) {
        return new AttachAddonCommand(subscriptionId, orderId, customer,
                "DATA_5GB", "DATA", new BigDecimal("49.90"), "TRY", "msg-1");
    }

    @Test
    void attachesAddonAuditsAndPublishesAddonAttachedEvent() {
        Subscription subscription = activeSubscription();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionAddonRepository.existsByOrderIdAndAddonCode(orderId, "DATA_5GB"))
                .thenReturn(false);

        UUID result = handler.handle(command(subscription.getId(), customerId));

        assertThat(result).isEqualTo(subscription.getId());

        ArgumentCaptor<SubscriptionAddon> addonCaptor = ArgumentCaptor.forClass(SubscriptionAddon.class);
        verify(subscriptionAddonRepository).save(addonCaptor.capture());
        SubscriptionAddon saved = addonCaptor.getValue();
        assertThat(saved.getSubscriptionId()).isEqualTo(subscription.getId());
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getAddonCode()).isEqualTo("DATA_5GB");
        assertThat(saved.getPrice()).isEqualByComparingTo("49.90");

        verify(auditLogWriter).log(eq("SUBSCRIPTION_ADDON_ATTACHED"), eq("Subscription"),
                eq(subscription.getId().toString()), any());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("subscription"), eq(subscription.getId().toString()),
                eq("subscription.addon-attached.v1"), payloadCaptor.capture());
        SubscriptionAddonAttachedV1 event = (SubscriptionAddonAttachedV1) payloadCaptor.getValue();
        assertThat(event.addonCode()).isEqualTo("DATA_5GB");
        assertThat(event.addonType()).isEqualTo("DATA");
        assertThat(event.price()).isEqualByComparingTo("49.90");
        assertThat(event.orderId()).isEqualTo(orderId.toString());
    }

    @Test
    void replayOfAlreadyAttachedOrderAddonIsANoOp() {
        Subscription subscription = activeSubscription();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionAddonRepository.existsByOrderIdAndAddonCode(orderId, "DATA_5GB"))
                .thenReturn(true);

        UUID result = handler.handle(command(subscription.getId(), customerId));

        assertThat(result).isEqualTo(subscription.getId());
        verify(subscriptionAddonRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void unknownSubscriptionThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(subscriptionRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command(missing, customerId)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void subscriptionOwnedByAnotherCustomerIsRejected() {
        Subscription subscription = activeSubscription();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> handler.handle(command(subscription.getId(), UUID.randomUUID())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong");
        verify(subscriptionAddonRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void terminatedSubscriptionCannotTakeAddons() {
        Subscription subscription = activeSubscription();
        subscription.terminate();
        when(subscriptionRepository.findById(subscription.getId()))
                .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> handler.handle(command(subscription.getId(), customerId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only ACTIVE");
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
