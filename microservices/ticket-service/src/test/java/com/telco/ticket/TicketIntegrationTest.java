package com.telco.ticket;

import com.telco.platform.mediator.Mediator;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.api.dto.TicketResponse;
import com.telco.ticket.application.command.AddCommentCommand;
import com.telco.ticket.application.command.AssignTicketCommand;
import com.telco.ticket.application.command.DetectSlaBreachCommand;
import com.telco.ticket.application.command.OpenTicketCommand;
import com.telco.ticket.application.command.ResolveTicketCommand;
import com.telco.ticket.application.query.GetTicketQuery;
import com.telco.ticket.domain.TicketStatus;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class TicketIntegrationTest {

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

    @Autowired private Mediator mediator;
    @Autowired private JdbcTemplate jdbc;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM ticket_comments");
        jdbc.execute("DELETE FROM tickets");
        customerId = UUID.randomUUID();
    }

    @Test
    void opening_ticket_sets_sla_and_assigned_team() {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                customerId, "BILLING", "HIGH", "Invoice discrepancy"));

        TicketResponse resp = mediator.query(new GetTicketQuery(ticketId, customerId, false));
        assertThat(resp.status()).isEqualTo("OPEN");
        assertThat(resp.assignedTeam()).isEqualTo("billing-support");
        assertThat(resp.slaDueAt()).isNotNull();
        assertThat(resp.comments()).isEmpty();
    }

    @Test
    void ticket_open_emits_ticket_opened_and_assigned_events() {
        mediator.send(new OpenTicketCommand(customerId, "TECHNICAL", "MEDIUM", "Cannot access app"));

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("ticket.opened.v1"), any());
        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("ticket.assigned.v1"), any());
    }

    @Test
    void resolve_ticket_transitions_to_resolved_and_emits_event() {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                customerId, "GENERAL", "LOW", "General inquiry"));

        mediator.send(new ResolveTicketCommand(ticketId));

        TicketResponse resp = mediator.query(new GetTicketQuery(ticketId, customerId, false));
        assertThat(resp.status()).isEqualTo("RESOLVED");
        assertThat(resp.resolvedAt()).isNotNull();

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("ticket.resolved.v1"), any());
    }

    @Test
    void resolve_is_idempotent() {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                customerId, "BILLING", "LOW", "Duplicate charge"));
        mediator.send(new ResolveTicketCommand(ticketId));
        mediator.send(new ResolveTicketCommand(ticketId));

        TicketResponse resp = mediator.query(new GetTicketQuery(ticketId, customerId, false));
        assertThat(resp.status()).isEqualTo("RESOLVED");
    }

    @Test
    void add_comment_persists_and_visible_in_get() {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                customerId, "BILLING", "MEDIUM", "Billing question"));
        UUID agentId = UUID.randomUUID();
        mediator.send(new AddCommentCommand(ticketId, agentId, "We are looking into this."));

        TicketResponse resp = mediator.query(new GetTicketQuery(ticketId, customerId, false));
        assertThat(resp.comments()).hasSize(1);
        assertThat(resp.comments().get(0).body()).isEqualTo("We are looking into this.");
    }

    @Test
    void assign_updates_team_and_status() {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                customerId, "TECHNICAL", "HIGH", "Network outage"));
        mediator.send(new AssignTicketCommand(ticketId, "escalation-team"));

        TicketResponse resp = mediator.query(new GetTicketQuery(ticketId, customerId, false));
        assertThat(resp.assignedTeam()).isEqualTo("escalation-team");
        assertThat(resp.status()).isEqualTo("ASSIGNED");
    }

    @Test
    void sla_breach_detection_emits_event_for_overdue_ticket() {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                customerId, "BILLING", "HIGH", "SLA test"));

        // Force sla_due_at to past
        jdbc.execute("UPDATE tickets SET sla_due_at = NOW() - INTERVAL '1 hour' WHERE id = '" + ticketId + "'");

        int breached = mediator.send(new DetectSlaBreachCommand());

        assertThat(breached).isEqualTo(1);
        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("ticket.sla-breached.v1"), any());
    }
}
