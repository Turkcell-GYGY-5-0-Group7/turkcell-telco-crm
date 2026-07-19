package com.telco.billing.application.handler;

import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.domain.BillCycle;
import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceLine;
import com.telco.billing.domain.InvoiceLineType;
import com.telco.billing.infrastructure.entity.AddonCharge;
import com.telco.billing.infrastructure.entity.OverageRecord;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.entity.TariffPrice;
import com.telco.billing.infrastructure.pdf.InvoicePdfRenderer;
import com.telco.billing.infrastructure.persistence.AddonChargeRepository;
import com.telco.billing.infrastructure.persistence.BillCycleRepository;
import com.telco.billing.infrastructure.persistence.InvoiceLineRepository;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.billing.infrastructure.persistence.OverageRecordRepository;
import com.telco.billing.infrastructure.persistence.TariffPriceRepository;
import com.telco.billing.infrastructure.storage.StorageService;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Processes one bill-run batch (a bounded slice of active subscribers) in its own transaction.
 *
 * <p>Extracted from {@link RunBillCommandHandler} so the bill-run (NFR-02, 100K subscribers in
 * under 30 minutes) can commit work in bounded, independent chunks instead of one multi-hundred-
 * thousand-row transaction, and so that {@link RunBillCommandHandler} can run several batches
 * concurrently. {@code REQUIRES_NEW} deliberately suspends the mediator's own outer transaction
 * (started by the platform {@code TransactionBehavior} around every command, see ADR-009) and opens
 * an independent one per batch, invocable safely from worker threads because Spring binds
 * transactional resources per-thread. This keeps the Domain Orchestration mode (ADR-004) and the
 * per-invoice outbox write (ADR-009) exactly as before; only the transaction boundary and the
 * calling thread changed — no business rule changed, and the outbox is never bypassed.
 */
@Component
public class BillRunBatchProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillRunBatchProcessor.class);
    // Lowercase outbox routing aggregate type: Debezium EventRouter routes to `<aggregate_type>.events`
    // (invoice.events); a PascalCase value would route to the wrong topic (event-catalog, ADR-009).
    private static final String OUTBOX_AGGREGATE_TYPE = "invoice";
    private static final String EVENT_INVOICE_GENERATED = "invoice.generated.v1";
    private static final BigDecimal KB_PER_MB = BigDecimal.valueOf(1024);

    private final TariffPriceRepository tariffPriceRepo;
    private final OverageRecordRepository overageRepo;
    private final AddonChargeRepository addonChargeRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceLineRepository invoiceLineRepo;
    private final BillCycleRepository billCycleRepo;
    private final InvoicePdfRenderer pdfRenderer;
    private final StorageService storageService;
    private final OutboxService outboxService;
    private final BigDecimal taxRate;
    private final int dueDays;

    public BillRunBatchProcessor(
            TariffPriceRepository tariffPriceRepo,
            OverageRecordRepository overageRepo,
            AddonChargeRepository addonChargeRepo,
            InvoiceRepository invoiceRepo,
            InvoiceLineRepository invoiceLineRepo,
            BillCycleRepository billCycleRepo,
            InvoicePdfRenderer pdfRenderer,
            StorageService storageService,
            OutboxService outboxService,
            @Value("${telco.billing.tax-rate:0.18}") String taxRateStr,
            @Value("${telco.billing.due-days:30}") int dueDays) {
        this.tariffPriceRepo = tariffPriceRepo;
        this.overageRepo = overageRepo;
        this.addonChargeRepo = addonChargeRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoiceLineRepo = invoiceLineRepo;
        this.billCycleRepo = billCycleRepo;
        this.pdfRenderer = pdfRenderer;
        this.storageService = storageService;
        this.outboxService = outboxService;
        this.taxRate = new BigDecimal(taxRateStr);
        this.dueDays = dueDays;
    }

    /**
     * Generates invoices for one batch of subscribers, in its own new transaction. Per-subscriber
     * failures are caught and logged so one bad record cannot fail the whole batch (matches the
     * previous per-subscriber isolation in the single-transaction implementation).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchOutcome processBatch(List<SubscriberBillingRecord> batch, RunBillCommand command) {
        int generated = 0;
        int skipped = 0;

        for (SubscriberBillingRecord subscriber : batch) {
            UUID subscriptionId = subscriber.getSubscriptionId();

            // Idempotency: skip if invoice already exists for this period. Backed by the
            // uidx_invoices_sub_period unique index as a defense-in-depth guarantee against
            // duplicate invoices even under concurrent batches (NFR-02).
            if (invoiceRepo.existsBySubscriptionIdAndPeriodStart(subscriptionId, command.periodStart())) {
                LOGGER.debug("Invoice already exists subscriptionId={} periodStart={} — skipping",
                        subscriptionId, command.periodStart());
                skipped++;
                continue;
            }

            try {
                // Unbilled addon fees attached before period end land on this invoice (FR-22).
                List<AddonCharge> addonCharges = addonChargeRepo
                        .findBySubscriptionIdAndBilledFalseAndAttachedAtBefore(
                                subscriptionId, command.periodEnd());

                Invoice invoice = generateInvoice(subscriber, command.periodStart(), command.periodEnd(),
                        addonCharges);
                invoiceRepo.save(invoice);
                invoiceLineRepo.saveAll(invoice.getLines());

                // Mark charges billed only after the invoice and its lines are persisted, so a
                // generateInvoice failure leaves them unbilled for the next run.
                addonCharges.forEach(AddonCharge::markBilled);
                addonChargeRepo.saveAll(addonCharges);

                byte[] pdfBytes = pdfRenderer.render(invoice);
                String pdfRef = storageService.store(
                        "invoices/" + invoice.getId() + ".pdf", pdfBytes, "application/pdf");
                invoice.attachPdf(pdfRef);
                invoiceRepo.save(invoice);

                ensureBillCycle(subscriber.getCustomerId(), command.periodEnd());

                outboxService.publish(OUTBOX_AGGREGATE_TYPE, invoice.getId().toString(), EVENT_INVOICE_GENERATED,
                        new InvoiceGeneratedEvent(
                                invoice.getId().toString(),
                                invoice.getCustomerId().toString(),
                                subscriptionId.toString(),
                                command.periodStart().toEpochMilli(),
                                command.periodEnd().toEpochMilli(),
                                invoice.getGrandTotal(),
                                invoice.getCurrency(),
                                invoice.getIssuedAt().toEpochMilli()));

                LOGGER.debug("Invoice generated invoiceId={} subscriptionId={} grandTotal={} {}",
                        invoice.getId(), subscriptionId, invoice.getGrandTotal(), invoice.getCurrency());
                generated++;
            } catch (Exception e) {
                LOGGER.error("Failed to generate invoice for subscriptionId={}", subscriptionId, e);
            }
        }

        return new BatchOutcome(generated, skipped);
    }

    private Invoice generateInvoice(SubscriberBillingRecord subscriber,
                                    Instant periodStart, Instant periodEnd,
                                    List<AddonCharge> addonCharges) {
        TariffPrice tariffPrice = tariffPriceRepo
                .findByTariffCode(subscriber.getTariffCode())
                .orElseThrow(() -> new IllegalStateException(
                        "No tariff price cached for tariffCode=" + subscriber.getTariffCode()));

        String currency = tariffPrice.getCurrency();
        LocalDate dueDate = periodEnd.atZone(ZoneOffset.UTC).toLocalDate().plusDays(dueDays);

        Invoice invoice = Invoice.create(
                subscriber.getCustomerId(), subscriber.getSubscriptionId(),
                periodStart, periodEnd, BigDecimal.ZERO, BigDecimal.ZERO, currency, dueDate);

        InvoiceLine.of(invoice, "Monthly tariff: " + subscriber.getTariffCode(),
                BigDecimal.ONE, tariffPrice.getMonthlyFee());

        // Add overage line if applicable.
        Optional<OverageRecord> overage = overageRepo.findBySubscriptionIdAndPeriodStart(
                subscriber.getSubscriptionId(), periodStart);
        if (overage.isPresent()) {
            OverageRecord ov = overage.get();
            if (ov.getDataOverageKb() > 0) {
                BigDecimal dataMb = BigDecimal.valueOf(ov.getDataOverageKb()).divide(KB_PER_MB, 4, RoundingMode.HALF_UP);
                BigDecimal dataOverageRate = new BigDecimal("0.10");
                InvoiceLine.of(invoice, "Data overage: " + dataMb.toPlainString() + " MB",
                        dataMb, dataOverageRate);
            }
            if (ov.getVoiceOverageSeconds() > 0) {
                BigDecimal minutes = BigDecimal.valueOf(ov.getVoiceOverageSeconds()).divide(
                        BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                BigDecimal voiceOverageRate = new BigDecimal("0.05");
                InvoiceLine.of(invoice, "Voice overage: " + minutes.toPlainString() + " min",
                        minutes, voiceOverageRate);
            }
            if (ov.getSmsOverageCount() > 0) {
                BigDecimal smsOverageRate = new BigDecimal("0.02");
                InvoiceLine.of(invoice, "SMS overage: " + ov.getSmsOverageCount() + " SMS",
                        BigDecimal.valueOf(ov.getSmsOverageCount()), smsOverageRate);
            }
        }

        // Addon and VAS fee lines (FR-22): one line per unbilled charge, typed for reporting.
        for (AddonCharge charge : addonCharges) {
            InvoiceLineType lineType = "VAS".equals(charge.getAddonType())
                    ? InvoiceLineType.VAS : InvoiceLineType.ADDON;
            InvoiceLine.of(invoice, (lineType == InvoiceLineType.VAS ? "VAS: " : "Addon: ")
                            + charge.getAddonCode(),
                    BigDecimal.ONE, charge.getPrice(), lineType);
        }

        // Recompute totals from lines.
        BigDecimal computedSubTotal = invoice.getLines().stream()
                .map(InvoiceLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal computedTax = computedSubTotal.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);

        // Replace invoice with correct totals via a new instance.
        Invoice final_ = Invoice.create(
                subscriber.getCustomerId(), subscriber.getSubscriptionId(),
                periodStart, periodEnd, computedSubTotal, computedTax, currency, dueDate);
        for (InvoiceLine line : invoice.getLines()) {
            // Preserve the line type on the rebuilt invoice - the 4-arg overload would silently
            // downgrade ADDON/VAS (and any future typed) lines to RECURRING.
            InvoiceLine.of(final_, line.getDescription(), line.getQuantity(), line.getUnitPrice(),
                    line.getLineType());
        }
        final_.issue();
        return final_;
    }

    private void ensureBillCycle(UUID customerId, Instant periodEnd) {
        if (billCycleRepo.findByCustomerId(customerId).isEmpty()) {
            LocalDate nextRunDate = periodEnd.atZone(ZoneOffset.UTC).toLocalDate();
            BillCycle cycle = BillCycle.create(customerId, 1, nextRunDate);
            billCycleRepo.save(cycle);
        }
    }

    /** Outcome of processing one batch: how many invoices were generated vs. skipped as duplicates. */
    public record BatchOutcome(int generated, int skipped) {}

    // Outbox event payload record.
    private record InvoiceGeneratedEvent(
            String invoiceId, String customerId, String subscriptionId,
            long periodStart, long periodEnd,
            BigDecimal grandTotal, String currency, long issuedAt) {}
}
