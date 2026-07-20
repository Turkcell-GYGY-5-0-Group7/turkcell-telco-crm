package com.telco.billing.application.handler;

import com.telco.billing.application.command.PlaceInvoiceOnDisputeHoldCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link PlaceInvoiceOnDisputeHoldCommand}. Provisional-hold invariant (ADR-028 Section 5,
 * load-bearing): changes {@code disputeStatus} from {@code NONE} to {@code ON_HOLD} and NOTHING
 * else - {@code grandTotal}/{@code subTotal}/{@code tax}/{@code status} are bit-for-bit identical
 * before and after.
 */
@Component
public class PlaceInvoiceOnDisputeHoldCommandHandler
        implements CommandHandler<PlaceInvoiceOnDisputeHoldCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceInvoiceOnDisputeHoldCommandHandler.class);

    private final InvoiceRepository invoiceRepo;

    public PlaceInvoiceOnDisputeHoldCommandHandler(InvoiceRepository invoiceRepo) {
        this.invoiceRepo = invoiceRepo;
    }

    @Override
    @Transactional
    public Void handle(PlaceInvoiceOnDisputeHoldCommand command) {
        Invoice invoice = invoiceRepo.findById(command.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + command.invoiceId()));

        invoice.placeOnDisputeHold();
        invoiceRepo.save(invoice);

        LOGGER.info("Invoice placed ON_HOLD invoiceId={}", command.invoiceId());
        return null;
    }
}
