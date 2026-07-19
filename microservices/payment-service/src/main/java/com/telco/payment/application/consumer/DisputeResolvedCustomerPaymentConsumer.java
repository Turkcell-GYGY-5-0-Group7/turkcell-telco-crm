package com.telco.payment.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.RefundPaymentCommand;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes {@code dispute.resolved-customer.v1} from {@code dispute.events}. If the referenced
 * payment is {@code COMPLETED}, dispatches the EXISTING, UNMODIFIED {@link RefundPaymentCommand} -
 * reusing payment-service's refund machinery rather than building a parallel one (ADR-028 Section 5).
 * If the payment's status is anything other than {@code COMPLETED} (never charged, or
 * billing-service's unpaid-invoice branch already applied), this is a safe no-op: read-side guard,
 * mirroring {@code SubscriptionActivationFailedEventConsumer}'s pattern - no command dispatched, no
 * inbox row written, so a redelivery re-evaluates to the same no-op.
 * {@link Payment#markRefunded()}'s existing "only COMPLETED can be refunded" guard is the second
 * line of defense, not the primary check.
 */
@Component
public class DisputeResolvedCustomerPaymentConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisputeResolvedCustomerPaymentConsumer.class);
    private static final String REFUND_REASON = "DISPUTE_RESOLVED_CUSTOMER";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "dispute.resolved-customer.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;

    public DisputeResolvedCustomerPaymentConsumer(Mediator mediator, ObjectMapper objectMapper,
                                                  PaymentRepository paymentRepository) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
    }

    @KafkaListener(topics = "dispute.events", groupId = "payment-service-dispute-resolved-customer",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDisputeResolvedCustomer(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring dispute event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.paymentId() == null) {
                // Unpaid-invoice branch - billing-service's ApplyDisputeAdjustmentCommand owns this case.
                return;
            }

            UUID paymentId = UUID.fromString(payload.paymentId());
            Optional<Payment> payment = paymentRepository.findById(paymentId);

            if (payment.isEmpty()) {
                LOGGER.info("No payment found for paymentId={}; nothing to refund, treating as no-op "
                        + "messageId={}", paymentId, messageId);
                return;
            }

            Payment existing = payment.get();
            if (existing.getStatus() != PaymentStatus.COMPLETED) {
                LOGGER.info("Payment {} is in status {} (not COMPLETED); treating dispute resolution "
                        + "as no-op messageId={}", existing.getId(), existing.getStatus(), messageId);
                return;
            }

            mediator.send(new RefundPaymentCommand(existing.getId(), REFUND_REASON, messageId));
            LOGGER.info("Dispute resolution: refunded paymentId={} messageId={}", existing.getId(), messageId);
        } catch (Exception e) {
            LOGGER.error("Failed to process dispute.resolved-customer.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("dispute.resolved-customer.v1 payment consumer failed", e);
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String disputeId, String paymentId) {}
}
