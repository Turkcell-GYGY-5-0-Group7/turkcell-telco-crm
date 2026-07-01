package com.telco.billing.application.handler;

import com.telco.billing.application.command.MarkInvoicePaidCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class MarkInvoicePaidCommandHandler implements CommandHandler<MarkInvoicePaidCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkInvoicePaidCommandHandler.class);
    private static final String AGGREGATE_TYPE = "Invoice";
    private static final String EVENT_INVOICE_PAID = "invoice.paid.v1";

    private final InvoiceRepository invoiceRepo;
    private final OutboxService outboxService;

    public MarkInvoicePaidCommandHandler(InvoiceRepository invoiceRepo, OutboxService outboxService) {
        this.invoiceRepo = invoiceRepo;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Void handle(MarkInvoicePaidCommand command) {
        Invoice invoice = invoiceRepo.findById(command.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + command.invoiceId()));

        if (invoice.getStatus().name().equals("PAID")) {
            LOGGER.info("Invoice already PAID invoiceId={} — idempotent skip", command.invoiceId());
            return null;
        }

        invoice.markPaid();
        invoiceRepo.save(invoice);

        outboxService.publish(AGGREGATE_TYPE, invoice.getId().toString(), EVENT_INVOICE_PAID,
                new InvoicePaidEvent(
                        invoice.getId().toString(),
                        invoice.getCustomerId().toString(),
                        Instant.now().toEpochMilli()));

        LOGGER.info("Invoice marked PAID invoiceId={}", command.invoiceId());
        return null;
    }

    private record InvoicePaidEvent(String invoiceId, String customerId, long paidAt) {}
}
