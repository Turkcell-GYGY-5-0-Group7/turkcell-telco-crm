package com.telco.ticket.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.AssignTicketCommand;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.domain.TicketStatus;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignTicketCommandHandlerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;

    private AssignTicketCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AssignTicketCommandHandler(ticketRepository, outboxService);
    }

    @Test
    void handle_assigns_team_and_publishes_ticket_assigned_event() {
        UUID ticketId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, customerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        handler.handle(new AssignTicketCommand(ticketId, "escalation-team"));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(ticket.getAssignedTeam()).isEqualTo("escalation-team");
        verify(ticketRepository).save(ticket);
        verify(outboxService).publish(eq("ticket"), eq(ticketId.toString()),
                eq("ticket.assigned.v1"), any());
    }

    @Test
    void handle_throws_resource_not_found_when_ticket_does_not_exist() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new AssignTicketCommand(unknownId, "any-team")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void handle_published_event_contains_assigned_team() {
        UUID ticketId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, customerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);

        handler.handle(new AssignTicketCommand(ticketId, "specialist-team"));

        verify(outboxService).publish(anyString(), anyString(),
                eq("ticket.assigned.v1"),
                any());
    }

    private static Ticket buildTicket(UUID id, UUID customerId) {
        Ticket t = Ticket.open(customerId, "BILLING", "HIGH", "Subject",
                "initial-team", Instant.now().plusSeconds(3600));
        setField(t, "id", id);
        return t;
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
