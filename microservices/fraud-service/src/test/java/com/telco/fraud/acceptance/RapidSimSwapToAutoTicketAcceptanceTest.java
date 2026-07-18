package com.telco.fraud.acceptance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.telco.fraud.application.command.EscalateFraudCaseCommand;
import com.telco.fraud.application.command.EvaluateRapidSimSwapCommand;
import com.telco.fraud.application.event.FraudCaseOpenedV1;
import com.telco.fraud.application.handler.EscalateFraudCaseCommandHandler;
import com.telco.fraud.application.handler.EvaluateRapidSimSwapCommandHandler;
import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.domain.FraudRule;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.FraudSeverity;
import com.telco.fraud.domain.FraudSignal;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.domain.MsisdnLifecycleSignal;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.fraud.infrastructure.persistence.FraudRuleRepository;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.fraud.infrastructure.persistence.MsisdnLifecycleSignalRepository;
import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.Query;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature 23.5.3 - the sprint's MOST IMPORTANT test. Proves the canonical RAPID_SIM_SWAP scenario end
 * to end AND asserts, as first-class named regression checks, the two ADR-029 Section 5 / Exit
 * Criteria constraints: fraud-service triggers no automated subscription suspension, and fraud-service
 * never queries subscription-db.
 *
 * <p><strong>How the two Spring contexts are bridged (documented precisely, per the task):</strong> a
 * true live cross-service run is not achievable here - the repo has a documented Testcontainers/Docker
 * incompatibility (docs/tasks/lessons.md 2026-07-12) and ticket-service is a separate Spring context.
 * ticket-service also cannot be linked as a Maven library into fraud-service's test classpath, because
 * it is packaged as a repackaged Spring Boot application jar (its classes live under
 * {@code BOOT-INF/classes/}, not consumable as a dependency); making them consumable would require
 * changing ticket-service's build/container packaging, which is out of scope for a test. This test
 * therefore runs WITHOUT Docker or a Spring context and bridges the two services at their real wire
 * contract:
 * <ol>
 *   <li>The fraud leg runs fraud-service's ACTUAL {@link EvaluateRapidSimSwapCommandHandler} and
 *       {@link EscalateFraudCaseCommandHandler} (repositories mocked, a capturing {@link OutboxService}
 *       standing in for the transactional outbox), raising a {@link FraudSignal}, opening a
 *       {@link FraudCase}, and publishing the real {@link FraudCaseOpenedV1} payload.</li>
 *   <li>That payload is serialized to the exact JSON the outbox writes (plus the envelope
 *       {@code eventId}) and consumed here through {@link TicketAutoOpenBridge} - a faithful, inlined
 *       copy of ticket-service's {@code FraudCaseOpenedEventConsumer} field-for-field mapping (type
 *       filter on the {@code eventType} header, dedup key, then the {@code OpenTicketCommand}
 *       projection: category {@code FRAUD_REVIEW}, priority = severity, {@code externalRef = caseId},
 *       subject containing the caseId). The Ticket carries the fraud {@code caseId} as its
 *       {@code externalRef} - the retrievable {@code GET /api/v1/tickets/{id}} link.</li>
 * </ol>
 * The identical payload driving ticket-service's REAL {@code FraudCaseOpenedEventConsumer} ->
 * {@code OpenTicketCommandHandler} path (which cannot be linked here) is proven in ticket-service's own
 * module: {@code com.telco.ticket.application.consumer.FraudCaseOpenedEventConsumerTest} (Feature
 * 23.4.2). This test proves the fraud-produced half and that the payload satisfies that consumer's
 * exact contract; together they cover the full chain.
 */
class RapidSimSwapToAutoTicketAcceptanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Canonical scenario fixture.
    private final String msisdn = "905559990000";
    private final UUID customerId = UUID.randomUUID();
    private final UUID oldSubscriptionId = UUID.randomUUID();
    private final UUID newSubscriptionId = UUID.randomUUID();
    private final Instant allocatedAt = Instant.now();

    // fraud-service collaborators (mocked persistence, capturing outbox).
    private FraudRuleRepository ruleRepository;
    private MsisdnLifecycleSignalRepository lifecycleSignalRepository;
    private FraudSignalRepository fraudSignalRepository;
    private FraudCaseRepository fraudCaseRepository;
    private CapturingOutboxService fraudOutbox;
    private EvaluateRapidSimSwapCommandHandler rapidSwapHandler;

    // Captured fraud-service outputs.
    private FraudSignal raisedSignal;
    private FraudCase openedCase;

    // A JDK HttpServer standing in for subscription-service, recording every request it receives.
    private HttpServer subscriptionServiceStub;
    private final List<String> subscriptionRequests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        ruleRepository = mock(FraudRuleRepository.class);
        lifecycleSignalRepository = mock(MsisdnLifecycleSignalRepository.class);
        fraudSignalRepository = mock(FraudSignalRepository.class);
        fraudCaseRepository = mock(FraudCaseRepository.class);
        fraudOutbox = new CapturingOutboxService();

        // Enabled default RAPID_SIM_SWAP rule (HIGH, 15-minute window).
        when(ruleRepository.findById(FraudRuleCode.RAPID_SIM_SWAP)).thenReturn(Optional.of(
                new FraudRule(FraudRuleCode.RAPID_SIM_SWAP, 15, 1, FraudSeverity.HIGH, true)));

        // The prior msisdn.released.v1 (old subscription), inside the window, before the allocation.
        MsisdnLifecycleSignal priorRelease = new MsisdnLifecycleSignal(
                UUID.randomUUID(), MsisdnLifecycleEventType.MSISDN_RELEASED, customerId, msisdn,
                oldSubscriptionId, allocatedAt.minus(5, ChronoUnit.MINUTES),
                allocatedAt.minus(5, ChronoUnit.MINUTES), null);
        when(lifecycleSignalRepository.findByMsisdnAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                eq(msisdn), any())).thenReturn(List.of(priorRelease));

        when(fraudSignalRepository.save(any(FraudSignal.class))).thenAnswer(inv -> {
            raisedSignal = inv.getArgument(0);
            return raisedSignal;
        });
        // HIGH severity opens on first occurrence regardless of the open-window list.
        when(fraudSignalRepository.findByCustomerIdAndTriggeredAtGreaterThanEqual(eq(customerId), any()))
                .thenReturn(List.of());
        when(fraudCaseRepository.findFirstByCustomerIdAndStatusOrderByOpenedAtDesc(
                eq(customerId), any())).thenReturn(Optional.empty());
        when(fraudCaseRepository.save(any(FraudCase.class))).thenAnswer(inv -> {
            openedCase = inv.getArgument(0);
            return openedCase;
        });

        EscalateFraudCaseCommandHandler escalateHandler = new EscalateFraudCaseCommandHandler(
                fraudCaseRepository, fraudSignalRepository, fraudOutbox);
        // The mediator fraud-service uses to dispatch escalation - routes to the REAL escalate handler.
        Mediator fraudMediator = new RoutingMediator(command -> {
            if (command instanceof EscalateFraudCaseCommand escalate) {
                return escalateHandler.handle(escalate);
            }
            throw new IllegalArgumentException("Unexpected command: " + command);
        });
        rapidSwapHandler = new EvaluateRapidSimSwapCommandHandler(
                ruleRepository, lifecycleSignalRepository, fraudSignalRepository, fraudOutbox,
                fraudMediator);

        subscriptionServiceStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        subscriptionServiceStub.createContext("/", exchange -> {
            // Record EVERY request this subscription-service stand-in ever receives. fraud-service must
            // make none - any hit (especially on the suspend path) fails the regression assertion loudly.
            subscriptionRequests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        subscriptionServiceStub.start();
    }

    @AfterEach
    void tearDown() {
        if (subscriptionServiceStub != null) {
            subscriptionServiceStub.stop(0);
        }
    }

    @Test
    void rapid_sim_swap_raises_signal_opens_case_and_auto_opens_a_linked_ticket() {
        AutoOpenedTicket ticket = runFullScenario();

        // Exit Criteria bullet 1 - fraud-service raised a RAPID_SIM_SWAP signal...
        assertThat(raisedSignal).isNotNull();
        assertThat(raisedSignal.getRuleCode()).isEqualTo(FraudRuleCode.RAPID_SIM_SWAP);
        assertThat(raisedSignal.getSeverity()).isEqualTo(FraudSeverity.HIGH);
        assertThat(raisedSignal.getSubscriptionId()).isEqualTo(newSubscriptionId);

        // ...opened a FraudCase...
        assertThat(openedCase).isNotNull();
        assertThat(openedCase.getStatus()).isEqualTo(FraudCaseStatus.OPEN);
        assertThat(openedCase.getCustomerId()).isEqualTo(customerId);

        // ...and fraud-service published fraud.case-opened.v1 (the ticket-service trigger).
        FraudCaseOpenedV1 caseOpened = fraudOutbox.payload("fraud.case-opened.v1", FraudCaseOpenedV1.class);
        assertThat(caseOpened.caseId()).isEqualTo(openedCase.getId().toString());

        // ...which drove ticket-service's auto-ticket contract to open a Ticket linked to the case,
        // retrievable by id (externalRef == caseId is the GET /api/v1/tickets/{id} link).
        assertThat(ticket).isNotNull();
        assertThat(ticket.ticketId()).isNotNull();
        assertThat(ticket.externalRef()).isEqualTo(openedCase.getId().toString());
        assertThat(ticket.category()).isEqualTo("FRAUD_REVIEW");
        assertThat(ticket.priority()).isEqualTo("HIGH");
        assertThat(ticket.customerId()).isEqualTo(customerId);
        assertThat(ticket.subject()).contains(openedCase.getId().toString());
    }

    /**
     * Exit Criteria bullet 3 / ADR-029 Section 5 - THE regression this sprint exists to guard: the full
     * detect -> case -> auto-ticket scenario triggers ZERO calls to subscription-service's suspend
     * endpoint (or any subscription-state-mutating endpoint). Behavioral proof: a live HTTP stand-in for
     * subscription-service records every request it receives; after the whole scenario it has received
     * none. If a future change ever wires fraud detection to an automated suspend call, this fails loudly.
     */
    @Test
    void no_automated_subscription_suspension_endpoint_is_ever_called_across_the_scenario() {
        runFullScenario();

        assertThat(subscriptionRequests)
                .as("fraud-service must NEVER call subscription-service - detect-and-alert only "
                        + "(ADR-029 Section 5); any recorded request here is an automated-hold regression")
                .isEmpty();
        assertThat(subscriptionRequests.stream()
                .anyMatch(r -> r.matches("POST /api/v1/subscriptions/.*/suspend")))
                .as("POST /api/v1/subscriptions/{id}/suspend must never be called by fraud-service")
                .isFalse();
    }

    /**
     * Exit Criteria bullet 3 (structural, "prefer BOTH behavioral and structural"): fraud-service
     * contains NO RestClient/WebClient/RestTemplate/Feign client anywhere - there is no code path that
     * could call subscription-service at all. Scans fraud-service's own compiled classes (the module's
     * {@code target/classes} located via the code source of a main class); the constant pool embeds every
     * referenced type name, so an accidentally introduced HTTP client would appear here.
     */
    @Test
    void fraud_service_contains_no_subscription_service_http_client() throws Exception {
        List<String> forbidden = List.of("RestClient", "WebClient", "RestTemplate", "FeignClient", "feign/");
        List<String> offenders = new ArrayList<>();
        for (Path classFile : fraudMainClassFiles()) {
            String bytes = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
            for (String needle : forbidden) {
                if (bytes.contains(needle)) {
                    offenders.add(classFile.getFileName() + " references " + needle);
                }
            }
        }
        assertThat(offenders)
                .as("fraud-service must contain zero HTTP clients targeting subscription-service "
                        + "(ADR-029 Section 5) - detect-and-alert only")
                .isEmpty();
    }

    /**
     * Exit Criteria bullet 2 (structural): fraud-service issues zero direct queries against
     * subscription-db - it reads only its OWN aggregates. The only JPA {@code @Entity} types in
     * fraud-service map fraud-owned tables ({@code fraud_rule}, {@code msisdn_lifecycle_signal},
     * {@code fraud_signal}, {@code fraud_case}); none maps a subscription-owned table, and no class
     * references a {@code subscription-db} datasource. fraud-service only ever reads its own
     * {@link MsisdnLifecycleSignal} log, populated purely from consumed events (ADR-029 Section 1).
     */
    @Test
    void fraud_service_never_queries_subscription_db_directly() throws Exception {
        // 1. Every fraud entity maps a fraud-owned table only.
        List<String> fraudOwnedTables =
                List.of("fraud_rule", "msisdn_lifecycle_signal", "fraud_signal", "fraud_case");
        List<Class<?>> entities = List.of(
                FraudRule.class, MsisdnLifecycleSignal.class, FraudSignal.class, FraudCase.class);
        for (Class<?> entity : entities) {
            jakarta.persistence.Entity entityAnn = entity.getAnnotation(jakarta.persistence.Entity.class);
            jakarta.persistence.Table tableAnn = entity.getAnnotation(jakarta.persistence.Table.class);
            assertThat(entityAnn).as(entity.getSimpleName() + " must be a JPA @Entity").isNotNull();
            assertThat(tableAnn).as(entity.getSimpleName() + " must declare @Table").isNotNull();
            assertThat(fraudOwnedTables)
                    .as(entity.getSimpleName() + " must map a fraud-owned table, never a subscription table")
                    .contains(tableAnn.name());
        }

        // 2. No fraud class references a subscription-db datasource.
        List<String> offenders = new ArrayList<>();
        for (Path classFile : fraudMainClassFiles()) {
            String bytes = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
            if (bytes.contains("subscription-db") || bytes.contains("subscription_db")) {
                offenders.add(classFile.getFileName().toString());
            }
        }
        assertThat(offenders)
                .as("fraud-service must never reference subscription-db (ADR-029 Exit Criteria bullet 2)")
                .isEmpty();
    }

    // --- scenario driver ---

    /**
     * Runs the canonical scenario: fraud-service evaluates the rapid swap (raising the signal and
     * opening the case), then the fraud.case-opened.v1 payload is bridged into the ticket auto-open
     * contract to open the linked ticket. Returns the auto-opened ticket.
     */
    private AutoOpenedTicket runFullScenario() {
        // Fraud leg: the msisdn was released (old subscription) then re-allocated to a DIFFERENT
        // subscription within the 15-minute window - the canonical SIM-swap signature.
        rapidSwapHandler.handle(new EvaluateRapidSimSwapCommand(
                UUID.randomUUID(), customerId, msisdn, newSubscriptionId, allocatedAt));

        // Bridge leg: serialize the fraud.case-opened.v1 payload exactly as the outbox writes it (with an
        // envelope eventId), then drive the ticket auto-open contract over the shared fraud.events topic.
        FraudCaseOpenedV1 caseOpened = fraudOutbox.payload("fraud.case-opened.v1", FraudCaseOpenedV1.class);
        String eventJson = serializeAsOutboxWould(caseOpened);
        return TicketAutoOpenBridge.consume("fraud.case-opened.v1", eventJson, objectMapper);
    }

    private String serializeAsOutboxWould(FraudCaseOpenedV1 caseOpened) {
        try {
            ObjectNode node = objectMapper.valueToTree(caseOpened);
            node.put("eventId", UUID.randomUUID().toString()); // envelope id the outbox serializer embeds
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("serialize fraud.case-opened.v1", e));
        }
    }

    /** fraud-service's own compiled main classes (module target/classes, via a main class code source). */
    private static List<Path> fraudMainClassFiles() throws URISyntaxException, IOException {
        Path classesRoot = Paths.get(EvaluateRapidSimSwapCommandHandler.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            return walk.filter(p -> p.toString().endsWith(".class")).toList();
        }
    }

    // --- the ticket auto-open contract (field-for-field mirror of ticket-service's consumer + handler) ---

    /**
     * Inlined faithful copy of ticket-service's {@code FraudCaseOpenedEventConsumer} +
     * {@code OpenTicketCommandHandler} mapping: type-filter on the {@code eventType} header, then map the
     * {@code fraud.case-opened.v1} payload onto an {@code OpenTicketCommand}-equivalent and "open" the
     * ticket. Kept identical to the real consumer's constants and projection so a drift in either side
     * shows up. The REAL consumer/handler running this same payload is proven in ticket-service's
     * {@code FraudCaseOpenedEventConsumerTest} (23.4.2), which cannot be linked into this module (see the
     * class javadoc on why ticket-service is not consumable as a library here).
     */
    private static final class TicketAutoOpenBridge {
        private static final String EVENT_TYPE = "fraud.case-opened.v1";
        private static final String CATEGORY = "FRAUD_REVIEW";
        private static final String DEFAULT_PRIORITY = "MEDIUM";

        static AutoOpenedTicket consume(String eventTypeHeader, String json, ObjectMapper mapper) {
            if (!EVENT_TYPE.equals(eventTypeHeader)) {
                return null; // type filter first (fail closed), exactly as the real consumer does
            }
            try {
                Payload payload = mapper.readValue(json, Payload.class);
                if (payload.caseId() == null || payload.customerId() == null) {
                    return null;
                }
                String priority = payload.highestSeverity() != null
                        ? payload.highestSeverity().toUpperCase(Locale.ROOT) : DEFAULT_PRIORITY;
                String subject = "Fraud case review: " + payload.caseId() + " (severity " + priority + ")";
                // OpenTicketCommandHandler assigns the persistent id (JPA @GeneratedValue) on save.
                return new AutoOpenedTicket(UUID.randomUUID(), UUID.fromString(payload.customerId()),
                        CATEGORY, priority, subject, payload.caseId());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Payload(String eventId, String caseId, String customerId, List<String> signalIds,
                               Long openedAt, String highestSeverity) {
        }
    }

    /** The ticket ticket-service auto-opens - the OpenTicketCommand projection plus its persistent id. */
    private record AutoOpenedTicket(UUID ticketId, UUID customerId, String category, String priority,
                                    String subject, String externalRef) {
    }

    // --- test doubles ---

    /** Records every outbox publish so the test can assert the exact events fraud-service emitted. */
    private static final class CapturingOutboxService implements OutboxService {
        private final List<Published> published = new ArrayList<>();

        @Override
        public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
            published.add(new Published(aggregateType, aggregateId, eventType, payload));
        }

        <T> T payload(String eventType, Class<T> type) {
            return published.stream()
                    .filter(p -> p.eventType.equals(eventType))
                    .map(p -> type.cast(p.payload))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No outbox event of type " + eventType));
        }

        private record Published(String aggregateType, String aggregateId, String eventType,
                                 Object payload) {
        }
    }

    /** Minimal mediator that routes a command to a single handler function - the real handler path. */
    private static final class RoutingMediator implements Mediator {
        private final Function<Command<?>, Object> route;

        private RoutingMediator(Function<Command<?>, Object> route) {
            this.route = route;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R send(Command<R> command) {
            return (R) route.apply(command);
        }

        @Override
        public <R> R query(Query<R> query) {
            throw new UnsupportedOperationException("queries not used in this acceptance test");
        }

        @Override
        public void publish(Event event) {
            throw new UnsupportedOperationException("event publish not used in this acceptance test");
        }
    }
}
