package com.telco.subscription.application.handler;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.subscription.application.SubscriptionActivationFailedEmitter;
import com.telco.subscription.application.command.FailSubscriptionActivationCommand;
import org.springframework.stereotype.Component;

/**
 * Emits {@code subscription.activation-failed.v1} for a TERMINAL pre-activation failure (order missing
 * (404), order lookup rejected (non-404 4xx), or multi-item order) so the onboarding saga compensates.
 * No subscription is created and no MSISDN is allocated.
 *
 * <p>Emission is delegated to {@link SubscriptionActivationFailedEmitter}, the single source of truth
 * for the event shape, so the payload (field order, null {@code subscriptionId}, audit row) is
 * byte-identical to the in-activation failure path
 * ({@code ActivateSubscriptionCommandHandler.emitActivationFailed}). The mediator
 * {@code TransactionBehavior} commits the audit row and the outbox row atomically (ADR-005, ADR-009).
 */
@Component
public class FailSubscriptionActivationCommandHandler
        implements CommandHandler<FailSubscriptionActivationCommand, Void> {

    private final SubscriptionActivationFailedEmitter emitter;

    public FailSubscriptionActivationCommandHandler(SubscriptionActivationFailedEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public Void handle(FailSubscriptionActivationCommand command) {
        emitter.emit(command.orderId(), command.customerId(), command.reason());
        return null;
    }
}
