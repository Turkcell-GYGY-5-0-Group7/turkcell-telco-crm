package com.telco.subscription.application.handler;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.SuspendSubscriptionsForNonPaymentCommand;
import com.telco.subscription.application.event.SubscriptionSuspendedV1;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.domain.SubscriptionStatus;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Suspends a customer's ACTIVE subscriptions for non-payment (FR-14, saga suspend path). Writes an
 * audit row and emits {@code subscription.suspended.v1} per suspended subscription via the outbox.
 *
 * <p>Idempotent: only ACTIVE subscriptions are suspended; already SUSPENDED/TERMINATED ones are
 * skipped, so reprocessing the same {@code payment.failed.v1} is a no-op (defence in depth alongside
 * the inbox dedup at the consumer). The mediator {@code TransactionBehavior} makes the updates, audit
 * rows, and outbox rows commit atomically (ADR-005, ADR-009).
 */
@Component
public class SuspendSubscriptionsForNonPaymentCommandHandler
        implements CommandHandler<SuspendSubscriptionsForNonPaymentCommand, Integer> {

    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_TYPE = "subscription.suspended.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public SuspendSubscriptionsForNonPaymentCommandHandler(SubscriptionRepository subscriptionRepository,
                                                           OutboxService outboxService,
                                                           AuditLogWriter auditLogWriter) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Integer handle(SuspendSubscriptionsForNonPaymentCommand command) {
        List<Subscription> active = subscriptionRepository
                .findByCustomerIdAndStatus(command.customerId(), SubscriptionStatus.ACTIVE);

        String reason = command.reason() != null ? command.reason() : "NON_PAYMENT";

        for (Subscription subscription : active) {
            subscription.suspend();
            subscriptionRepository.save(subscription);

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
        }

        return active.size();
    }
}
