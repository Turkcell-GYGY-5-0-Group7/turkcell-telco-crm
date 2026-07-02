package com.telco.ticket.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.ResolveTicketCommand;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveTicketCommandHandlerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;

    private ResolveTicketCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResolveTicketCommandHandler(ticketRepository, outboxService);
    }

    @Test
    void handle_resolves_ticket_saves_and_publishes_resolved_event() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, UUID.randomUUID());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);

        handler.handle(new ResolveTicketCommand(ticketId));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        verify(ticketRepository).save(ticket);
        verify(outboxService).publish(eq("ticket"), eq(ticketId.toString()),
                eq("ticket.resolved.v1"), any());
    }

    @Test
    void handle_is_idempotent_for_already_resolved_ticket() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, UUID.randomUUID());
        ticket.resolve(); // pre-resolve
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        handler.handle(new ResolveTicketCommand(ticketId));

        // No save or event on a second resolve; handler returns early
        verify(ticketRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void handle_throws_resource_not_found_when_ticket_does_not_exist() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ResolveTicketCommand(unknownId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    private static Ticket buildTicket(UUID id, UUID customerId) {
        Ticket t = Ticket.open(customerId, "BILLING", "HIGH", "Subject",
                "team", Instant.now().plusSeconds(3600));
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
