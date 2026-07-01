package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.ReactivateSubscriptionCommand;
import com.telco.subscription.application.event.SubscriptionActivatedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Reactivates a suspended subscription (SUSPENDED -> ACTIVE), writing an audit row and re-emitting
 * {@code subscription.activated.v1} via the outbox (FR-14).
 *
 * <p>The event-catalog defines no dedicated reactivation event; reactivation returns the subscription
 * to ACTIVE, so we re-emit {@code subscription.activated.v1} (the subscription is again active)
 * rather than invent an uncataloged event. The mediator {@code TransactionBehavior} makes the
 * subscription update, the audit row, and the outbox row commit atomically (ADR-005, ADR-009). The
 * SUSPENDED-only guard lives in {@link Subscription#reactivate()} and raises
 * {@code BusinessRuleException} on an illegal transition.
 */
@Component
public class ReactivateSubscriptionCommandHandler
        implements CommandHandler<ReactivateSubscriptionCommand, UUID> {

    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_TYPE = "subscription.activated.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public ReactivateSubscriptionCommandHandler(SubscriptionRepository subscriptionRepository,
                                                OutboxService outboxService,
                                                AuditLogWriter auditLogWriter) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UUID handle(ReactivateSubscriptionCommand command) {
        Subscription subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));

        if (!command.callerIsAdmin()
                && !subscription.getCustomerId().toString().equals(command.callerUserId())) {
            throw new AccessDeniedException("Subscription does not belong to caller");
        }

        subscription.reactivate();
        subscriptionRepository.save(subscription);

        auditLogWriter.log(
                "SUBSCRIPTION_REACTIVATED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("customerId", subscription.getCustomerId().toString()));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_TYPE,
                new SubscriptionActivatedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        subscription.getMsisdn(),
                        subscription.getTariffCode(),
                        Instant.now().toEpochMilli(),
                        // Manual reactivation is not saga-driven: no order correlates it, so orderId is
                        // null (the Avro contract allows null for non-saga/manual activation).
                        null));

        return subscription.getId();
    }
}
