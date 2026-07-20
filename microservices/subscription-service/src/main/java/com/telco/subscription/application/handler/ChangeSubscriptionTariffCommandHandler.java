package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.ChangeSubscriptionTariffCommand;
import com.telco.subscription.application.event.SubscriptionTariffChangedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a PLAN_CHANGE order (FR-09): swaps the tariff code on the ACTIVE subscription and
 * publishes {@code subscription.tariff-changed.v1} - consumed by billing-service (next-cycle
 * pricing) and by order-service (fulfils the plan-change order). Mediator TransactionBehavior
 * makes the update, audit row, and outbox row atomic (ADR-005, ADR-009).
 */
@Component
public class ChangeSubscriptionTariffCommandHandler
        implements CommandHandler<ChangeSubscriptionTariffCommand, UUID> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ChangeSubscriptionTariffCommandHandler.class);
    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_TYPE = "subscription.tariff-changed.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public ChangeSubscriptionTariffCommandHandler(SubscriptionRepository subscriptionRepository,
                                                  OutboxService outboxService,
                                                  AuditLogWriter auditLogWriter) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UUID handle(ChangeSubscriptionTariffCommand command) {
        Subscription subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));
        if (!subscription.getCustomerId().equals(command.customerId())) {
            throw new BusinessRuleException(
                    "Subscription " + command.subscriptionId() + " does not belong to customer "
                            + command.customerId());
        }

        String oldTariffCode = subscription.changeTariff(command.newTariffCode());
        subscriptionRepository.save(subscription);

        auditLogWriter.log(
                "SUBSCRIPTION_TARIFF_CHANGED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("orderId", command.orderId().toString(),
                        "oldTariffCode", oldTariffCode,
                        "newTariffCode", command.newTariffCode()));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_TYPE,
                new SubscriptionTariffChangedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        command.orderId().toString(),
                        oldTariffCode,
                        command.newTariffCode(),
                        Instant.now().toEpochMilli()));

        LOGGER.info("Tariff changed subscriptionId={} orderId={} {} -> {}",
                subscription.getId(), command.orderId(), oldTariffCode, command.newTariffCode());
        return subscription.getId();
    }
}
