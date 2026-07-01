package com.telco.payment.application.command;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Refunds a COMPLETED payment. Only COMPLETED -> REFUNDED is allowed (FR-27).
 *
 * <p>Idempotency: the command implements {@link IdempotentRequest} so the
 * {@link com.telco.platform.inbox.InboxBehavior} dedups duplicate Kafka deliveries of
 * {@code subscription.activation-failed.v1} INSIDE the refund transaction (ADR-005). On the saga
 * compensation path the {@code messageId} is the Kafka record key (the outbox event id); on the
 * REST/admin path the caller supplies a fresh, unique key so the inbox guard is a harmless
 * per-request no-op rather than a null-key insert. The domain state machine (COMPLETED -> REFUNDED)
 * remains the defence-in-depth guard against a double refund through a different message.
 */
public record RefundPaymentCommand(

        @NotNull
        UUID paymentId,

        @NotBlank @Size(max = 500)
        String reason,

        /**
         * Inbox idempotency key. On the saga path this is the Kafka message id of the
         * {@code subscription.activation-failed.v1} delivery; the inbox row is written atomically with
         * the refund. Must be non-null and stable per logical message.
         */
        @NotBlank @Size(max = 255)
        String messageId

) implements Command<PaymentResponse>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
