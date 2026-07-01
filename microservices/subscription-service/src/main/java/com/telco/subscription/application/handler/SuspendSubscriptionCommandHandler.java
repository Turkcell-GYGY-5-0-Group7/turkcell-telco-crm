package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.SuspendSubscriptionCommand;
import com.telco.subscription.application.event.SubscriptionSuspendedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Suspends a subscription (ACTIVE -> SUSPENDED), writing an audit row and emitting
 * {@code subscription.suspended.v1} via the outbox (FR-14).
 *
 * <p>The mediator {@code TransactionBehavior} makes the subscription update, the audit row, and the
 * outbox row commit atomically (ADR-005, ADR-009). The ACTIVE-only guard lives in
 * {@link Subscription#suspend()} and raises {@code BusinessRuleException} on an illegal transition.
 */
@Component
public class SuspendSubscriptionCommandHandler
        implements CommandHandler<SuspendSubscriptionCommand, UUID> {

    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_TYPE = "subscription.suspended.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public SuspendSubscriptionCommandHandler(SubscriptionRepository subscriptionRepository,
                                             OutboxService outboxService,
                                             AuditLogWriter auditLogWriter) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UUID handle(SuspendSubscriptionCommand command) {
        Subscription subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));

        subscription.suspend();
        subscriptionRepository.save(subscription);

        String reason = command.reason() != null ? command.reason() : "MANUAL";

        auditLogWriter.log(
                "SUBSCRIPTION_SUSPENDED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("reason", reason));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_TYPE,
                new SubscriptionSuspendedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        reason,
                        Instant.now().toEpochMilli()));

        return subscription.getId();
    }
}
