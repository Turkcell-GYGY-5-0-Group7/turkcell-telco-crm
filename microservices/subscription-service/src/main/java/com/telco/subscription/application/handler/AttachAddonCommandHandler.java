package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.AttachAddonCommand;
import com.telco.subscription.application.event.SubscriptionAddonAttachedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.domain.SubscriptionAddon;
import com.telco.subscription.domain.SubscriptionStatus;
import com.telco.subscription.infrastructure.SubscriptionAddonRepository;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Applies an ADDON order (FR-09): records the addon on the ACTIVE subscription and publishes
 * {@code subscription.addon-attached.v1} - consumed by billing-service (invoices the fee, FR-22)
 * and by order-service (fulfils the addon order). Idempotent to redelivery: inbox dedup first, and
 * a check on the {@code (orderId, addonCode)} unique key no-ops a replay that slipped past it.
 */
@Component
public class AttachAddonCommandHandler implements CommandHandler<AttachAddonCommand, UUID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachAddonCommandHandler.class);
    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_TYPE = "subscription.addon-attached.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAddonRepository subscriptionAddonRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public AttachAddonCommandHandler(SubscriptionRepository subscriptionRepository,
                                     SubscriptionAddonRepository subscriptionAddonRepository,
                                     OutboxService outboxService,
                                     AuditLogWriter auditLogWriter) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAddonRepository = subscriptionAddonRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UUID handle(AttachAddonCommand command) {
        Subscription subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));
        if (!subscription.getCustomerId().equals(command.customerId())) {
            throw new BusinessRuleException(
                    "Subscription " + command.subscriptionId() + " does not belong to customer "
                            + command.customerId());
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot attach addon to subscription in status: " + subscription.getStatus().name()
                            + ". Only ACTIVE subscriptions may take addons.");
        }

        if (subscriptionAddonRepository.existsByOrderIdAndAddonCode(command.orderId(), command.addonCode())) {
            LOGGER.info("Addon already attached for orderId={} addonCode={} - skipping replay",
                    command.orderId(), command.addonCode());
            return subscription.getId();
        }

        SubscriptionAddon addon = SubscriptionAddon.attach(
                subscription.getId(), command.orderId(), command.addonCode(),
                command.addonType(), command.price(), command.currency());
        subscriptionAddonRepository.save(addon);

        auditLogWriter.log(
                "SUBSCRIPTION_ADDON_ATTACHED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("orderId", command.orderId().toString(),
                        "addonCode", command.addonCode()));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_TYPE,
                new SubscriptionAddonAttachedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        command.orderId().toString(),
                        command.addonCode(),
                        command.addonType(),
                        command.price(),
                        command.currency(),
                        Instant.now().toEpochMilli()));

        LOGGER.info("Addon attached subscriptionId={} orderId={} addonCode={}",
                subscription.getId(), command.orderId(), command.addonCode());
        return subscription.getId();
    }
}
