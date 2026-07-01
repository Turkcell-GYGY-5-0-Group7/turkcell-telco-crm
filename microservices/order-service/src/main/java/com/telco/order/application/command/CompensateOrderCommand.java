package com.telco.order.application.command;

import com.telco.order.application.dto.OrderResponse;
import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Compensates the onboarding saga by cancelling an order after {@code payment.refunded.v1} (saga step
 * COMPENSATED / status CANCELLED). Dispatched ONLY by the {@code payment.refunded.v1} consumer as a
 * trusted system actor - it carries no caller identity and bypasses the customer ownership guard.
 *
 * <p>Distinct from the customer-facing {@link CancelOrderCommand}: keeping the saga compensation on
 * its own command lets it implement {@link IdempotentRequest} (atomic inbox dedup on the Kafka
 * {@code messageId}) without changing the customer cancel path's semantics (which must still throw
 * 422 on a double-cancel) (tech-lead ruling 2a/2b, shared-command care). Drives
 * {@code Order.cancel()} (PENDING or CONFIRMED -&gt; CANCELLED) and publishes {@code order.cancelled.v1}.
 */
public record CompensateOrderCommand(

        @NotNull
        UUID orderId,

        String reason,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotNull
        String messageId

) implements Command<OrderResponse>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
