package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.SubscriptionActivationFailedEmitter;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.event.MsisdnAllocatedV1;
import com.telco.subscription.application.event.SubscriptionActivatedV1;
import com.telco.subscription.domain.MsisdnAllocationService;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Activates a new subscription as part of the new-line onboarding saga (FR-13, AC-01 step 5).
 *
 * <p>On SUCCESS: atomically allocates a FREE MSISDN, persists the subscription in ACTIVE state,
 * writes an audit row, and emits BOTH {@code msisdn.allocated.v1} and {@code subscription.activated.v1}
 * via the outbox. The mediator {@code TransactionBehavior} wraps this handler in one transaction, so
 * the MSISDN status flip ({@code FOR UPDATE SKIP LOCKED} row), the subscription insert, the audit row,
 * and the outbox rows all commit atomically (ADR-005, ADR-009). The row lock taken during allocation
 * is held to commit, guaranteeing no two activations share an MSISDN.
 *
 * <p>On FAILURE (no FREE MSISDN to allocate): {@code MsisdnAllocationService.allocate()} throws a
 * {@link BusinessRuleException} BEFORE any pool row is modified, so the pool is unchanged - no number
 * is allocated. The handler catches that, emits {@code subscription.activation-failed.v1} keyed by
 * the saga's {@code orderId} for compensation (consumers wired in 9.4.3), and returns {@code null}
 * (no subscription was created). Because the handler returns normally rather than rethrowing, the
 * transaction COMMITS with only the failure outbox row - the all-or-nothing guarantee: either an
 * ACTIVE subscription with an allocated number, or no allocation at all plus a failure event. A
 * {@link BusinessRuleException} from {@code Subscription.activate(...)} after allocation is NOT caught
 * here - it propagates and rolls the whole transaction back (allocation included), which is correct.
 */
@Component
public class ActivateSubscriptionCommandHandler
        implements CommandHandler<ActivateSubscriptionCommand, UUID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivateSubscriptionCommandHandler.class);

    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_MSISDN_ALLOCATED = "msisdn.allocated.v1";
    private static final String EVENT_SUBSCRIPTION_ACTIVATED = "subscription.activated.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final MsisdnAllocationService msisdnAllocationService;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;
    private final SubscriptionActivationFailedEmitter activationFailedEmitter;

    public ActivateSubscriptionCommandHandler(SubscriptionRepository subscriptionRepository,
                                              MsisdnAllocationService msisdnAllocationService,
                                              OutboxService outboxService,
                                              AuditLogWriter auditLogWriter,
                                              SubscriptionActivationFailedEmitter activationFailedEmitter) {
        this.subscriptionRepository = subscriptionRepository;
        this.msisdnAllocationService = msisdnAllocationService;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
        this.activationFailedEmitter = activationFailedEmitter;
    }

    @Override
    public UUID handle(ActivateSubscriptionCommand command) {
        String msisdn;
        try {
            msisdn = msisdnAllocationService.allocate();
        } catch (BusinessRuleException e) {
            // Pool exhausted (or other allocation rule failure) BEFORE any pool row was written:
            // no number is allocated. Emit the compensation event and let the transaction commit
            // with only this failure row (no subscription, no allocation).
            emitActivationFailed(command, e.getMessage());
            return null;
        }

        Subscription subscription = Subscription.activate(
                command.customerId(), msisdn, command.tariffCode(), command.tariffVersion());
        subscriptionRepository.save(subscription);

        long now = Instant.now().toEpochMilli();

        auditLogWriter.log(
                "SUBSCRIPTION_ACTIVATED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("msisdn", msisdn, "customerId", command.customerId().toString()));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_MSISDN_ALLOCATED,
                new MsisdnAllocatedV1(
                        msisdn,
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        now));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_SUBSCRIPTION_ACTIVATED,
                new SubscriptionActivatedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        msisdn,
                        subscription.getTariffCode(),
                        now,
                        command.orderId().toString()));

        return subscription.getId();
    }

    private void emitActivationFailed(ActivateSubscriptionCommand command, String reason) {
        // Delegate to the single source of truth for the event shape so the in-activation failure path
        // (MSISDN pool exhausted) emits a byte-identical payload + audit row to the consumer's
        // pre-activation terminal failures (order 404 / lookup rejected / multi-item) routed through
        // FailSubscriptionActivationCommand. orderId is required (enforced by the command's compact
        // constructor), so the failure event always carries the non-nullable saga correlation key.
        activationFailedEmitter.emit(command.orderId(), command.customerId(), mapReason(reason));
    }

    /** Maps a domain failure message to a stable reason code carried in the event. */
    private static String mapReason(String message) {
        if (message != null && message.contains("pool exhausted")) {
            return "MSISDN_POOL_EXHAUSTED";
        }
        return "ACTIVATION_FAILED";
    }
}
