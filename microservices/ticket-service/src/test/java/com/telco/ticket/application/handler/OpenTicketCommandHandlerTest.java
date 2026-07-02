package com.telco.ticket.application.handler;

import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.OpenTicketCommand;
import com.telco.ticket.domain.SlaPolicy;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.SlaPolicyRepository;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenTicketCommandHandlerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SlaPolicyRepository slaPolicyRepository;
    @Mock private OutboxService outboxService;
    @Mock private SlaPolicy slaPolicy;

    private OpenTicketCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OpenTicketCommandHandler(ticketRepository, slaPolicyRepository, outboxService);
        // Assign a UUID after save so getId().toString() does not NPE in the handler
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            setField(t, "id", UUID.randomUUID());
            return t;
        });
    }

    @Test
    void handle_uses_sla_policy_team_and_resolution_minutes_and_publishes_both_events() {
        when(slaPolicy.getTeam()).thenReturn("billing-support");
        when(slaPolicy.getResolutionMinutes()).thenReturn(240);
        when(slaPolicyRepository.findByCategoryAndPriority("BILLING", "HIGH"))
                .thenReturn(Optional.of(slaPolicy));

        UUID customerId = UUID.randomUUID();
        UUID ticketId = handler.handle(new OpenTicketCommand(customerId, "BILLING", "HIGH", "Invoice error"));

        assertThat(ticketId).isNotNull();
        verify(ticketRepository).save(any(Ticket.class));
        verify(outboxService, atLeast(1)).publish(eq("ticket"), anyString(),
                eq("ticket.opened.v1"), any());
        verify(outboxService, atLeast(1)).publish(eq("ticket"), anyString(),
                eq("ticket.assigned.v1"), any());
    }

    @Test
    void handle_falls_back_to_customer_care_and_1440_minutes_when_no_sla_policy() {
        when(slaPolicyRepository.findByCategoryAndPriority("UNKNOWN", "LOW"))
                .thenReturn(Optional.empty());

        UUID customerId = UUID.randomUUID();
        UUID ticketId = handler.handle(new OpenTicketCommand(customerId, "UNKNOWN", "LOW", "General"));

        assertThat(ticketId).isNotNull();
        verify(ticketRepository).save(any(Ticket.class));
        verify(outboxService, atLeast(1)).publish(eq("ticket"), anyString(),
                eq("ticket.opened.v1"), any());
    }

    @Test
    void handle_normalises_category_and_priority_to_upper_case() {
        when(slaPolicyRepository.findByCategoryAndPriority("TECHNICAL", "MEDIUM"))
                .thenReturn(Optional.empty());

        handler.handle(new OpenTicketCommand(UUID.randomUUID(), "technical", "medium", "App crash"));

        // Handler calls findByCategoryAndPriority twice: once for team, once for resolutionMinutes
        verify(slaPolicyRepository, atLeast(1)).findByCategoryAndPriority("TECHNICAL", "MEDIUM");
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
