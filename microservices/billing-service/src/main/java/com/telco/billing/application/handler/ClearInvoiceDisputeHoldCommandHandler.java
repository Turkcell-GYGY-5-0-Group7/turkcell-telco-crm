package com.telco.billing.application.handler;

import com.telco.billing.application.command.ClearInvoiceDisputeHoldCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link ClearInvoiceDisputeHoldCommand}. Like the hold itself, clearing it (ADR-028
 * Section 5, {@code RESOLVED_MERCHANT}) changes {@code disputeStatus} back to {@code NONE} and
 * NOTHING else.
 */
@Component
public class ClearInvoiceDisputeHoldCommandHandler
        implements CommandHandler<ClearInvoiceDisputeHoldCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearInvoiceDisputeHoldCommandHandler.class);

    private final InvoiceRepository invoiceRepo;

    public ClearInvoiceDisputeHoldCommandHandler(InvoiceRepository invoiceRepo) {
        this.invoiceRepo = invoiceRepo;
    }

    @Override
    @Transactional
    public Void handle(ClearInvoiceDisputeHoldCommand command) {
        Invoice invoice = invoiceRepo.findById(command.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + command.invoiceId()));

        invoice.clearDisputeHold();
        invoiceRepo.save(invoice);

        LOGGER.info("Invoice dispute hold cleared invoiceId={}", command.invoiceId());
        return null;
    }
}
