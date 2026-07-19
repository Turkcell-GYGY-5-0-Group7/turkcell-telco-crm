package com.telco.payment.application.command;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.domain.PaymentMethod;
import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Initiates a PSP charge for an order.
 *
 * <p>Two complementary idempotency layers protect this command, each guarding a different failure:
 * <ul>
 *   <li><b>Inbox dedup ({@code idempotencyKey})</b> - guards against duplicate Kafka <em>deliveries</em>
 *       of the SAME {@code order.created.v1} message. The {@link com.telco.platform.inbox.InboxBehavior}
 *       inserts the inbox row INSIDE the handler transaction (ADR-005 exactly-once-effect): the inbox
 *       row, the payment write and the outbox event commit or roll back together. The key is the Kafka
 *       record key (the outbox event id set by the relay) on the saga path. On the REST/admin path the
 *       caller supplies its own stable {@code idempotencyKey} (the {@code paymentRequestId}), so the
 *       inbox guard is a harmless per-request no-op rather than a null-key insert.</li>
 *   <li><b>Command-level dedup ({@code paymentRequestId})</b> - guards against the SAME order being
 *       charged twice through DIFFERENT messages (e.g. a fresh inbox key after a compaction or a manual
 *       admin replay). The handler looks the payment up by {@code paymentRequestId} (derived from
 *       {@code orderId}) and returns the existing payment for a terminal state instead of re-charging
 *       (FR-25, FR-26).</li>
 * </ul>
 * Both layers stay active and are independent: the inbox protects the delivery, the
 * {@code paymentRequestId} protects the business effect.
 */
public record ChargePaymentCommand(

        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        /**
         * Invoice this payment settles, when the charge originates from an invoice payment
         * (Section 14.2 pay-invoice flow) rather than the order saga. Nullable: most charges are
         * order-driven. Populated onto the new {@link com.telco.payment.domain.Payment} and carried
         * through to {@code payment.completed.v1}/{@code payment.failed.v1} so billing-service's
         * {@code PaymentCompletedBillingConsumer} can mark the invoice paid.
         */
        UUID invoiceId,

        /**
         * How the customer pays (FR-25). Nullable: the handler defaults a {@code null} method to
         * {@link PaymentMethod#CREDIT_CARD}. Label only in the MVP - the mock PSP ignores it
         * (Sprint 24 design-note D6).
         */
        PaymentMethod method,

        /**
         * Idempotency key, typically derived from {@code orderId}. A stable key ensures that
         * duplicate Kafka deliveries of {@code order.created.v1} do not double-charge the customer.
         */
        @NotBlank @Size(max = 64)
        String paymentRequestId,

        /**
         * Inbox idempotency key. On the saga path this is the Kafka message id of the
         * {@code order.created.v1} delivery; the inbox row is written atomically with the charge so a
         * handler rollback re-arms redelivery. Must be non-null and stable per logical message.
         */
        @NotBlank @Size(max = 255)
        String messageId

) implements Command<PaymentResponse>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
