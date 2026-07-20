package com.telco.billing.application.handler;

import com.telco.billing.application.command.ApplyDisputeAdjustmentCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceStatus;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link ApplyDisputeAdjustmentCommand}: the real half of the provisional-credit model on
 * the billing side (ADR-028 Section 5). If the invoice is already {@code PAID}, this is a safe
 * no-op - the paid case is payment-service's responsibility (Feature 22.5.3), and billing-service
 * must never issue a second, competing resolution for the same dispute. Otherwise delegates to
 * {@link Invoice#applyDisputeAdjustment}, whose own check-then-act guard (only when
 * {@code disputeStatus == ON_HOLD}) is the second line of defense against a duplicate adjustment.
 */
@Component
public class ApplyDisputeAdjustmentCommandHandler
        implements CommandHandler<ApplyDisputeAdjustmentCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplyDisputeAdjustmentCommandHandler.class);

    private final InvoiceRepository invoiceRepo;

    public ApplyDisputeAdjustmentCommandHandler(InvoiceRepository invoiceRepo) {
        this.invoiceRepo = invoiceRepo;
    }

    @Override
    @Transactional
    public Void handle(ApplyDisputeAdjustmentCommand command) {
        Invoice invoice = invoiceRepo.findById(command.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + command.invoiceId()));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            LOGGER.info("Invoice {} already PAID - dispute adjustment is payment-service's "
                    + "responsibility, no-op", command.invoiceId());
            return null;
        }

        invoice.applyDisputeAdjustment(command.resolutionAmount());
        invoiceRepo.save(invoice);

        LOGGER.info("Applied dispute adjustment invoiceId={} amount={}",
                command.invoiceId(), command.resolutionAmount());
        return null;
    }
}
