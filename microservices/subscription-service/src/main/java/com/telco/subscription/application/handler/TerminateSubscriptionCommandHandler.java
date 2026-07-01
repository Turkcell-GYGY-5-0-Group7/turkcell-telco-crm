package com.telco.subscription.application.handler;

import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.TerminateSubscriptionCommand;
import com.telco.subscription.application.event.MsisdnReleasedV1;
import com.telco.subscription.application.event.SubscriptionTerminatedV1;
import com.telco.subscription.domain.MsisdnAllocationService;
import com.telco.subscription.domain.Subscription;
import com.telco.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Terminates a subscription (ACTIVE/SUSPENDED -> TERMINATED) and returns its MSISDN to the pool
 * (FREE), writing an audit row and emitting {@code msisdn.released.v1} via the outbox (FR-13, FR-14).
 *
 * <p>The mediator {@code TransactionBehavior} makes the subscription update, the MSISDN release, the
 * audit row, and the outbox row commit atomically (ADR-005, ADR-009). The TERMINATED -> TERMINATED
 * guard lives in {@link Subscription#terminate()} and raises {@code BusinessRuleException}.
 */
@Component
public class TerminateSubscriptionCommandHandler
        implements CommandHandler<TerminateSubscriptionCommand, UUID> {

    private static final String AGGREGATE_TYPE = "Subscription";
    private static final String OUTBOX_AGGREGATE_TYPE = "subscription";
    private static final String EVENT_MSISDN_RELEASED = "msisdn.released.v1";
    private static final String EVENT_SUBSCRIPTION_TERMINATED = "subscription.terminated.v1";

    private final SubscriptionRepository subscriptionRepository;
    private final MsisdnAllocationService msisdnAllocationService;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public TerminateSubscriptionCommandHandler(SubscriptionRepository subscriptionRepository,
                                               MsisdnAllocationService msisdnAllocationService,
                                               OutboxService outboxService,
                                               AuditLogWriter auditLogWriter) {
        this.subscriptionRepository = subscriptionRepository;
        this.msisdnAllocationService = msisdnAllocationService;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UUID handle(TerminateSubscriptionCommand command) {
        Subscription subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + command.subscriptionId()));

        if (!command.callerIsAdmin()
                && !subscription.getCustomerId().toString().equals(command.callerUserId())) {
            throw new AccessDeniedException("Subscription does not belong to caller");
        }

        subscription.terminate();
        subscriptionRepository.save(subscription);

        String msisdn = subscription.getMsisdn();
        msisdnAllocationService.release(msisdn);

        auditLogWriter.log(
                "SUBSCRIPTION_TERMINATED",
                AGGREGATE_TYPE,
                subscription.getId().toString(),
                Map.of("msisdn", msisdn));

        long now = Instant.now().toEpochMilli();

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_MSISDN_RELEASED,
                new MsisdnReleasedV1(
                        msisdn,
                        subscription.getId().toString(),
                        now
                ));

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                subscription.getId().toString(),
                EVENT_SUBSCRIPTION_TERMINATED,
                new SubscriptionTerminatedV1(
                        subscription.getId().toString(),
                        subscription.getCustomerId().toString(),
                        msisdn,
                        now
                ));

        return subscription.getId();
    }
}
