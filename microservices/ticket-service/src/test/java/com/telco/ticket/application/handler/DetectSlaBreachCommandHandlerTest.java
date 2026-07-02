package com.telco.ticket.application.handler;

import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.DetectSlaBreachCommand;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectSlaBreachCommandHandlerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;

    private DetectSlaBreachCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DetectSlaBreachCommandHandler(ticketRepository, outboxService);
    }

    @Test
    void handle_publishes_sla_breached_event_and_marks_each_overdue_ticket() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, UUID.randomUUID());
        when(ticketRepository.findBreached(any(Instant.class))).thenReturn(List.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);

        int count = handler.handle(new DetectSlaBreachCommand());

        assertThat(count).isEqualTo(1);
        assertThat(ticket.getSlaBreachedAt()).isNotNull();
        verify(outboxService).publish(eq("ticket"), eq(ticketId.toString()),
                eq("ticket.sla-breached.v1"), any());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void handle_returns_zero_and_does_not_publish_when_no_breached_tickets() {
        when(ticketRepository.findBreached(any(Instant.class))).thenReturn(List.of());

        int count = handler.handle(new DetectSlaBreachCommand());

        assertThat(count).isEqualTo(0);
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void handle_processes_multiple_overdue_tickets_independently() {
        Ticket t1 = buildTicket(UUID.randomUUID(), UUID.randomUUID());
        Ticket t2 = buildTicket(UUID.randomUUID(), UUID.randomUUID());
        when(ticketRepository.findBreached(any(Instant.class))).thenReturn(List.of(t1, t2));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = handler.handle(new DetectSlaBreachCommand());

        assertThat(count).isEqualTo(2);
        assertThat(t1.getSlaBreachedAt()).isNotNull();
        assertThat(t2.getSlaBreachedAt()).isNotNull();
    }

    private static Ticket buildTicket(UUID id, UUID customerId) {
        // slaDueAt is in the past to simulate a genuinely overdue ticket
        Ticket t = Ticket.open(customerId, "BILLING", "HIGH", "Overdue",
                "team", Instant.now().minusSeconds(60));
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
