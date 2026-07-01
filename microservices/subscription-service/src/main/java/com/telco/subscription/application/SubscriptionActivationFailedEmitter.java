package com.telco.subscription.application;

import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.event.SubscriptionActivationFailedV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for emitting {@code subscription.activation-failed.v1} so the event shape is
 * byte-identical regardless of which path failed: the MSISDN-pool exhaustion path inside
 * {@code ActivateSubscriptionCommandHandler}, or the consumer's terminal saga errors (order 404,
 * unsupported multi-item order) routed through {@code FailSubscriptionActivationCommand}.
 *
 * <p>The event is keyed (aggregateId) by {@code orderId} so the compensation chain (payment refund,
 * order cancel) can correlate by order. {@code subscriptionId} is always {@code null} on these paths
 * (no subscription was created); the Avro contract permits null there. Field order
 * (orderId, customerId, subscriptionId=null, reason, failedAt) matches
 * {@link SubscriptionActivationFailedV1} field-for-field.
 *
 * <p>Must be called inside a transaction (the mediator {@code TransactionBehavior} wraps command
 * handlers) so the outbox row and the audit row commit atomically (ADR-009, ADR-021).
 */
@Component
public class SubscriptionActivationFailedEmitter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionActivationFailedEmitter.class);
    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_ACTIVATION_FAILED = "subscription.activation-failed.v1";

    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public SubscriptionActivationFailedEmitter(OutboxService outboxService,
                                               AuditLogWriter auditLogWriter) {
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    /**
     * Publishes {@code subscription.activation-failed.v1} (keyed by {@code orderId}) plus the
     * mandatory audit row. {@code reasonCode} is a stable, already-mapped reason code carried on the
     * event.
     */
    public void emit(UUID orderId, UUID customerId, String reasonCode) {
        LOGGER.warn("Subscription activation failed orderId={} customerId={} reason={}",
                orderId, customerId, reasonCode);

        auditLogWriter.log(
                "SUBSCRIPTION_ACTIVATION_FAILED",
                AGGREGATE_TYPE,
                null,
                Map.of("customerId", customerId.toString(), "reason", reasonCode));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                orderId.toString(),
                EVENT_ACTIVATION_FAILED,
                new SubscriptionActivationFailedV1(
                        orderId.toString(),
                        customerId.toString(),
                        null,
                        reasonCode,
                        Instant.now().toEpochMilli()));
    }
}
