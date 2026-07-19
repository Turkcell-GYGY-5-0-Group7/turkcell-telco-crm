package com.telco.usage.application.handler;

import com.telco.usage.application.command.TopUpQuotaCommand;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies a purchased addon's allowance deltas to the subscription's active quota (Sprint 24
 * Feature 24.3, design-note D4).
 *
 * <p>The quota row is loaded with the same pessimistic write lock the CDR metering path uses, so a
 * concurrent decrement cannot lost-update the remaining counters.
 *
 * <p><b>No quota row yet is a TRANSIENT condition, not an error:</b> for an addon bundled into a
 * NEW_LINE order, {@code addon.purchased.v1} (order fulfillment) and the activation-driven quota
 * provisioning race on independent topics - the top-up can arrive before
 * {@code ProvisionQuotaCommandHandler} has created the period's row. The handler throws so the
 * transaction (including the inbox dedup row) rolls back and Kafka redelivers; by then
 * provisioning has normally caught up. This mirrors order-service's TRANSIENT/TERMINAL split on
 * the fulfill leg.
 */
@Component
public class TopUpQuotaCommandHandler implements CommandHandler<TopUpQuotaCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopUpQuotaCommandHandler.class);

    private final QuotaRepository quotaRepository;

    public TopUpQuotaCommandHandler(QuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    @Override
    public Void handle(TopUpQuotaCommand command) {
        Quota quota = quotaRepository
                .findActiveForUpdateBySubscriptionId(command.subscriptionId(), command.occurredAt())
                .orElseThrow(() -> new IllegalStateException(
                        "No active quota for subscription " + command.subscriptionId()
                                + " at " + command.occurredAt() + " - activation provisioning may "
                                + "still be in flight; retry on redelivery"));

        quota.addAllowance(command.minutes(), command.sms(), command.mb());
        quotaRepository.save(quota);

        LOGGER.info("Quota topped up subscriptionId={} +minutes={} +sms={} +mb={} "
                        + "remaining now minutes={} sms={} mb={}",
                command.subscriptionId(), command.minutes(), command.sms(), command.mb(),
                quota.getMinutesRemaining(), quota.getSmsRemaining(), quota.getMbRemaining());
        return null;
    }
}
