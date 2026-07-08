package com.telco.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.events.testsupport.AvroContractAssertions;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.AssignTicketCommand;
import com.telco.ticket.application.command.DetectSlaBreachCommand;
import com.telco.ticket.application.command.OpenTicketCommand;
import com.telco.ticket.application.command.ResolveTicketCommand;
import com.telco.ticket.application.handler.AssignTicketCommandHandler;
import com.telco.ticket.application.handler.DetectSlaBreachCommandHandler;
import com.telco.ticket.application.handler.OpenTicketCommandHandler;
import com.telco.ticket.application.handler.ResolveTicketCommandHandler;
import com.telco.ticket.domain.SlaPolicy;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.SlaPolicyRepository;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Event-schema contract gate for the ticket domain events (feature 14.1.2, ADR-019, NFR-16; extended
 * feature 14.5 phase 6).
 *
 * <p>ticket-service builds its outbox payloads inline as {@code Map.of(...)} rather than typed
 * records, so there is no compile-time type to reflect on. This test drives each command handler with
 * Mockito mocks, captures the actual payload passed to {@link OutboxService#publish}, and compares its
 * keys, runtime value types, and observed nullness <b>against the canonical Avro schema loaded
 * directly from {@code platform-event-contracts}</b> (not a hand-copied local {@code .avsc} snapshot:
 * see the 14.5 tracking doc, phase 6, for why that check was self-referential and never verified the
 * governed contract). A removed, renamed, retyped, or added map key fails the build, blocking a
 * backward-incompatible change to a published ticket event. No Kafka, Schema Registry, or Spring
 * context is booted.
 *
 * <p>Guarded events: {@code ticket.opened.v1}, {@code ticket.assigned.v1}, {@code ticket.resolved.v1},
 * {@code ticket.sla-breached.v1}.
 */
@ExtendWith(MockitoExtension.class)
class TicketEventContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AVRO_PACKAGE = "com.telco.platform.events.ticket.";

    @Mock private TicketRepository ticketRepository;
    @Mock private SlaPolicyRepository slaPolicyRepository;
    @Mock private OutboxService outboxService;
    @Mock private SlaPolicy slaPolicy;

    @Test
    void open_ticket_emits_opened_and_assigned_matching_contract() {
        when(slaPolicy.getTeam()).thenReturn("billing-support");
        when(slaPolicy.getResolutionMinutes()).thenReturn(240);
        lenient().when(slaPolicyRepository.findByCategoryAndPriority("BILLING", "HIGH"))
                .thenReturn(Optional.of(slaPolicy));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            setField(t, "id", UUID.randomUUID());
            return t;
        });

        new OpenTicketCommandHandler(ticketRepository, slaPolicyRepository, outboxService)
                .handle(new OpenTicketCommand(UUID.randomUUID(), "BILLING", "HIGH", "Invoice error"));

        Map<String, Map<String, Object>> emitted = capturePayloads();
        assertMatchesContract("ticket.opened.v1", "TicketOpenedV1", emitted);
        assertMatchesContract("ticket.assigned.v1", "TicketAssignedV1", emitted);
    }

    @Test
    void assign_ticket_emits_assigned_matching_contract() {
        Ticket ticket = openTicketWithId();
        when(ticketRepository.findById(any(UUID.class))).thenReturn(Optional.of(ticket));

        new AssignTicketCommandHandler(ticketRepository, outboxService)
                .handle(new AssignTicketCommand(ticket.getId(), "tech-support"));

        assertMatchesContract("ticket.assigned.v1", "TicketAssignedV1", capturePayloads());
    }

    @Test
    void resolve_ticket_emits_resolved_matching_contract() {
        Ticket ticket = openTicketWithId();
        when(ticketRepository.findById(any(UUID.class))).thenReturn(Optional.of(ticket));

        new ResolveTicketCommandHandler(ticketRepository, outboxService)
                .handle(new ResolveTicketCommand(ticket.getId()));

        assertMatchesContract("ticket.resolved.v1", "TicketResolvedV1", capturePayloads());
    }

    @Test
    void detect_sla_breach_emits_sla_breached_matching_contract() {
        Ticket ticket = openTicketWithId();
        when(ticketRepository.findBreached(any(Instant.class))).thenReturn(List.of(ticket));

        new DetectSlaBreachCommandHandler(ticketRepository, outboxService)
                .handle(new DetectSlaBreachCommand());

        assertMatchesContract("ticket.sla-breached.v1", "TicketSlaBreachedV1", capturePayloads());
    }

    // --- helpers ---

    private Map<String, Map<String, Object>> capturePayloads() {
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(outboxService, org.mockito.Mockito.atLeastOnce())
                .publish(any(), any(), eventType.capture(), payload.capture());

        Map<String, Map<String, Object>> byType = new HashMap<>();
        List<String> types = eventType.getAllValues();
        List<Object> payloads = payload.getAllValues();
        for (int i = 0; i < types.size(); i++) {
            Map<String, Object> asMap = MAPPER.convertValue(payloads.get(i), new TypeReference<>() {});
            byType.put(types.get(i), asMap);
        }
        return byType;
    }

    private static void assertMatchesContract(String eventType, String canonicalRecordName,
            Map<String, Map<String, Object>> emitted) {
        assertThat(emitted)
                .as("handler did not publish %s", eventType)
                .containsKey(eventType);

        Schema schema = AvroContractAssertions.canonicalSchema(AVRO_PACKAGE + canonicalRecordName);
        AvroContractAssertions.assertPayloadMatchesSchema(schema, emitted.get(eventType));
    }

    private static Ticket openTicketWithId() {
        Ticket ticket = Ticket.open(UUID.randomUUID(), "TECHNICAL", "MEDIUM", "App crash",
                "tech-support", Instant.now().plus(1, ChronoUnit.HOURS));
        setField(ticket, "id", UUID.randomUUID());
        return ticket;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
