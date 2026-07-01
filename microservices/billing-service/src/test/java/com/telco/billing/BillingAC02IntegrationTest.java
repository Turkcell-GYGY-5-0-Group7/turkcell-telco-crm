package com.telco.billing;

import com.telco.billing.application.command.MarkInvoicePaidCommand;
import com.telco.billing.application.command.MarkInvoicesOverdueCommand;
import com.telco.billing.application.command.RecordOverageCommand;
import com.telco.billing.application.command.RecordSubscriptionActivatedCommand;
import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceStatus;
import com.telco.billing.infrastructure.client.ProductCatalogBillingClient;
import com.telco.billing.infrastructure.client.TariffPricingResponse;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-02 Integration Test — Billing (FR-21, FR-22, FR-23, FR-24).
 *
 * <p>Drives the full billing pipeline via the mediator against a real Postgres. Mocks Kafka
 * (OutboxService, InboxService) and ProductCatalogBillingClient so the test runs in CI.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class BillingAC02IntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @MockitoBean private OutboxService outboxService;
    @MockitoBean private InboxService inboxService;
    @MockitoBean private ProductCatalogBillingClient productCatalogClient;

    @Autowired private Mediator mediator;
    @Autowired private InvoiceRepository invoiceRepo;
    @Autowired private JdbcTemplate jdbc;

    private UUID subscriptionId;
    private UUID customerId;
    private Instant periodStart;
    private Instant periodEnd;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM invoice_lines");
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM overage_records");
        jdbc.execute("DELETE FROM tariff_prices");
        jdbc.execute("DELETE FROM subscriber_billing_records");
        jdbc.execute("DELETE FROM bill_cycles");

        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        periodStart = monthStart(Instant.now());
        periodEnd = ZonedDateTime.ofInstant(periodStart, ZoneOffset.UTC).plusMonths(1).toInstant();

        when(productCatalogClient.getTariffPricing(anyString()))
                .thenReturn(new TariffPricingResponse("POSTPAID-S", "Postpaid S", new BigDecimal("99.99"), "TRY"));
    }

    @Test
    void bill_run_generates_invoice_for_active_subscriber() {
        activateSubscription();

        RunBillResult result = mediator.send(new RunBillCommand(periodStart, periodEnd));

        assertThat(result.invoicesGenerated()).isEqualTo(1);
        assertThat(result.invoicesSkipped()).isEqualTo(0);

        Invoice invoice = findInvoice().orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getGrandTotal()).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.getCurrency()).isEqualTo("TRY");
        assertThat(invoice.getPdfRef()).isNotNull();
        assertThat(invoice.getLines()).isNotEmpty();

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("invoice.generated.v1"), any());
    }

    @Test
    void bill_run_is_idempotent_same_period_no_duplicate() {
        activateSubscription();

        mediator.send(new RunBillCommand(periodStart, periodEnd));
        RunBillResult secondRun = mediator.send(new RunBillCommand(periodStart, periodEnd));

        assertThat(secondRun.invoicesGenerated()).isEqualTo(0);
        assertThat(secondRun.invoicesSkipped()).isEqualTo(1);

        long count = invoiceRepo.findAll().stream()
                .filter(i -> i.getSubscriptionId().equals(subscriptionId)).count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void invoice_includes_tariff_fee_line() {
        activateSubscription();
        mediator.send(new RunBillCommand(periodStart, periodEnd));

        Invoice invoice = findInvoice().orElseThrow();
        boolean hasTariffLine = invoice.getLines().stream()
                .anyMatch(l -> l.getDescription().contains("POSTPAID-S"));
        assertThat(hasTariffLine).isTrue();
    }

    @Test
    void overage_creates_additional_invoice_line() {
        activateSubscription();

        mediator.send(new RecordOverageCommand(
                subscriptionId, periodStart, periodEnd,
                0, 0, 2048, Instant.now()));

        mediator.send(new RunBillCommand(periodStart, periodEnd));

        Invoice invoice = findInvoice().orElseThrow();
        boolean hasOverageLine = invoice.getLines().stream()
                .anyMatch(l -> l.getDescription().contains("overage"));
        assertThat(hasOverageLine).isTrue();
        assertThat(invoice.getLines().size()).isGreaterThan(1);
    }

    @Test
    void payment_marks_invoice_paid_and_emits_invoice_paid_event() {
        activateSubscription();
        mediator.send(new RunBillCommand(periodStart, periodEnd));

        Invoice invoice = findInvoice().orElseThrow();
        mediator.send(new MarkInvoicePaidCommand(invoice.getId()));

        Invoice paid = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("invoice.paid.v1"), any());
    }

    @Test
    void paid_invoice_is_idempotent_on_second_payment() {
        activateSubscription();
        mediator.send(new RunBillCommand(periodStart, periodEnd));
        Invoice invoice = findInvoice().orElseThrow();

        mediator.send(new MarkInvoicePaidCommand(invoice.getId()));
        mediator.send(new MarkInvoicePaidCommand(invoice.getId()));

        Invoice paid = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void overdue_detection_marks_past_due_invoices() {
        activateSubscription();
        mediator.send(new RunBillCommand(periodStart, periodEnd));

        // Force due_date to yesterday.
        jdbc.execute("UPDATE invoices SET due_date = CURRENT_DATE - 1 WHERE subscription_id = '"
                + subscriptionId + "'");

        int count = mediator.send(new MarkInvoicesOverdueCommand());

        assertThat(count).isEqualTo(1);
        Invoice overdue = findInvoice().orElseThrow();
        assertThat(overdue.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("invoice.overdue.v1"), any());
    }

    // --- helpers ---

    private void activateSubscription() {
        mediator.send(new RecordSubscriptionActivatedCommand(
                subscriptionId, customerId, "POSTPAID-S", Instant.now()));
    }

    private Optional<Invoice> findInvoice() {
        return invoiceRepo.findBySubscriptionIdAndPeriodStartWithLines(subscriptionId, periodStart);
    }

    private static Instant monthStart(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
    }
}
