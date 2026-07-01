package com.telco.billing.application.handler;

import com.telco.billing.application.command.MarkInvoicesOverdueCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceStatus;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class MarkInvoicesOverdueCommandHandler implements CommandHandler<MarkInvoicesOverdueCommand, Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkInvoicesOverdueCommandHandler.class);
    // Lowercase outbox routing aggregate type -> `invoice.events` topic (event-catalog, ADR-009).
    private static final String OUTBOX_AGGREGATE_TYPE = "invoice";
    private static final String EVENT_INVOICE_OVERDUE = "invoice.overdue.v1";

    private final InvoiceRepository invoiceRepo;
    private final OutboxService outboxService;

    public MarkInvoicesOverdueCommandHandler(InvoiceRepository invoiceRepo, OutboxService outboxService) {
        this.invoiceRepo = invoiceRepo;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Integer handle(MarkInvoicesOverdueCommand command) {
        List<Invoice> overdueInvoices = invoiceRepo.findOverdue(InvoiceStatus.ISSUED, LocalDate.now());
        int count = 0;

        for (Invoice invoice : overdueInvoices) {
            invoice.markOverdue();
            invoiceRepo.save(invoice);

            outboxService.publish(OUTBOX_AGGREGATE_TYPE, invoice.getId().toString(), EVENT_INVOICE_OVERDUE,
                    new InvoiceOverdueEvent(
                            invoice.getId().toString(),
                            invoice.getCustomerId().toString(),
                            invoice.getDueDate().toString(),
                            Instant.now().toEpochMilli()));

            LOGGER.info("Invoice marked OVERDUE invoiceId={} dueDate={}", invoice.getId(), invoice.getDueDate());
            count++;
        }

        return count;
    }

    private record InvoiceOverdueEvent(String invoiceId, String customerId,
                                       String dueDate, long detectedAt) {}
}
