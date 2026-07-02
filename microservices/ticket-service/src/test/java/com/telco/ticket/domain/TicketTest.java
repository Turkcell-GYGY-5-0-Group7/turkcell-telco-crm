package com.telco.ticket.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Instant SLA_DUE = Instant.now().plusSeconds(3600);

    @Test
    void open_creates_ticket_with_open_status_and_all_fields_set() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "BILLING", "HIGH", "Invoice error",
                "billing-support", SLA_DUE);

        assertThat(ticket.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(ticket.getCategory()).isEqualTo("BILLING");
        assertThat(ticket.getPriority()).isEqualTo("HIGH");
        assertThat(ticket.getSubject()).isEqualTo("Invoice error");
        assertThat(ticket.getAssignedTeam()).isEqualTo("billing-support");
        assertThat(ticket.getSlaDueAt()).isEqualTo(SLA_DUE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getUpdatedAt()).isNotNull();
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getSlaBreachedAt()).isNull();
        assertThat(ticket.getComments()).isEmpty();
    }

    @Test
    void assign_transitions_status_to_assigned_and_updates_team() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "TECHNICAL", "MEDIUM", "App crash",
                "general-support", SLA_DUE);

        ticket.assign("escalation-team");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(ticket.getAssignedTeam()).isEqualTo("escalation-team");
        assertThat(ticket.getUpdatedAt()).isNotNull();
    }

    @Test
    void resolve_transitions_status_to_resolved_and_stamps_resolved_at() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "BILLING", "LOW", "Minor query",
                "billing-support", SLA_DUE);

        ticket.resolve();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(ticket.getUpdatedAt()).isEqualTo(ticket.getResolvedAt());
    }

    @Test
    void mark_sla_breached_stamps_sla_breached_at_without_changing_status() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "BILLING", "HIGH", "Overdue",
                "billing-support", SLA_DUE);

        ticket.markSlaBreached();

        assertThat(ticket.getSlaBreachedAt()).isNotNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void add_comment_appends_comment_with_correct_body_and_author() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "BILLING", "MEDIUM", "Question",
                "billing-support", SLA_DUE);
        UUID agentId = UUID.randomUUID();

        TicketComment comment = ticket.addComment(agentId, "Looking into this now.");

        assertThat(comment.getAuthorId()).isEqualTo(agentId);
        assertThat(comment.getBody()).isEqualTo("Looking into this now.");
        assertThat(comment.getCreatedAt()).isNotNull();
        assertThat(ticket.getComments()).hasSize(1);
        assertThat(ticket.getComments().get(0).getBody()).isEqualTo("Looking into this now.");
    }

    @Test
    void add_multiple_comments_all_appear_in_comments_list() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "TECHNICAL", "HIGH", "Network issue",
                "tech-support", SLA_DUE);

        ticket.addComment(UUID.randomUUID(), "First response");
        ticket.addComment(UUID.randomUUID(), "Second response");

        assertThat(ticket.getComments()).hasSize(2);
    }

    @Test
    void open_to_assigned_to_resolved_full_lifecycle() {
        Ticket ticket = Ticket.open(CUSTOMER_ID, "GENERAL", "LOW", "General inquiry",
                "customer-care", SLA_DUE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);

        ticket.assign("specialist-team");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);

        ticket.resolve();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
    }
}
