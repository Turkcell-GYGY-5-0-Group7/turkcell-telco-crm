package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.SubscriptionActivationFailedEmitter;
import com.telco.subscription.application.command.ChangeTariffCommand;
import com.telco.subscription.application.event.SubscriptionTariffChangedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies a PLAN_CHANGE order to its target subscription (Sprint 24 Feature 24.4, design-note D2).
 *
 * <p>On SUCCESS: re-validates the subscription exists, belongs to the ordering customer, and is
 * ACTIVE (order-creation already validated all three, but payment-to-here is asynchronous - the
 * subscription may have been terminated or transferred in between), applies
 * {@link Subscription#changeTariff}, writes the audit row, and emits
 * {@code subscription.tariff-changed.v1} keyed by the subscription id. The mediator
 * {@code TransactionBehavior} makes the update, audit and outbox row atomic (ADR-005, ADR-009).
 *
 * <p>On TERMINAL FAILURE (subscription gone, not owned, not ACTIVE, or already on the tariff):
 * reuses {@link SubscriptionActivationFailedEmitter} - a documented event-name reuse (D2) - so the
 * EXISTING refund/cancel compensation saga runs with zero new consumers, and returns {@code null}
 * so the transaction commits with only the failure row. A future v2 may split the event.
 */
@Component
public class ChangeTariffCommandHandler implements CommandHandler<ChangeTariffCommand, UUID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeTariffCommandHandler.class);

    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_TARIFF_CHANGED = "subscription.tariff-changed.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;
    private final SubscriptionActivationFailedEmitter activationFailedEmitter;

    public ChangeTariffCommandHandler(SubscriptionRepository subscriptionRepository,
                                      OutboxService outboxService,
                                      AuditLogWriter auditLogWriter,
                                      SubscriptionActivationFailedEmitter activationFailedEmitter) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
        this.activationFailedEmitter = activationFailedEmitter;
    }

    @Override
    public UUID handle(ChangeTariffCommand command) {
        Optional<Subscription> found = subscriptionRepository.findById(command.subscriptionId());
        if (found.isEmpty()) {
            activationFailedEmitter.emit(command.orderId(), command.customerId(),
                    "SUBSCRIPTION_NOT_FOUND");
            return null;
        }
        Subscription subscription = found.get();

        if (!command.customerId().equals(subscription.getCustomerId())) {
            activationFailedEmitter.emit(command.orderId(), command.customerId(),
                    "SUBSCRIPTION_NOT_OWNED");
            return null;
        }

        String previousTariffCode = subscription.getTariffCode();
        try {
            subscription.changeTariff(command.newTariffCode(), command.newTariffVersion());
        } catch (BusinessRuleException e) {
            // Not ACTIVE, or already on the requested tariff: terminal - compensate (D2).
            LOGGER.warn("changeTariff rejected for subscription {} order {}: {}",
                    subscription.getId(), command.orderId(), e.getMessage());
            activationFailedEmitter.emit(command.orderId(), command.customerId(),
                    "TARIFF_CHANGE_REJECTED");
            return null;
        }
        subscriptionRepository.save(subscription);

        auditLogWriter.log(
                "SUBSCRIPTION_TARIFF_CHANGED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("orderId", command.orderId().toString(),
                        "previousTariffCode", previousTariffCode,
                        "newTariffCode", command.newTariffCode()));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_TARIFF_CHANGED,
                new SubscriptionTariffChangedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        subscription.getMsisdn(),
                        previousTariffCode,
                        command.newTariffCode(),
                        command.newTariffVersion(),
                        command.orderId().toString(),
                        Instant.now().toEpochMilli()));

        LOGGER.info("Subscription {} tariff changed {} -> {} (order {})",
                subscription.getId(), previousTariffCode, command.newTariffCode(), command.orderId());
        return subscription.getId();
    }
}
