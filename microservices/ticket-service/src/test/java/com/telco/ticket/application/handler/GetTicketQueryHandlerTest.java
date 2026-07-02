package com.telco.ticket.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.ticket.api.dto.TicketResponse;
import com.telco.ticket.application.query.GetTicketQuery;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTicketQueryHandlerTest {

    @Mock private TicketRepository ticketRepository;

    private GetTicketQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetTicketQueryHandler(ticketRepository);
    }

    @Test
    void handle_returns_response_when_caller_is_the_ticket_owner() {
        UUID ticketId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, customerId);
        when(ticketRepository.findByIdWithComments(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = handler.handle(new GetTicketQuery(ticketId, customerId, false));

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.comments()).isEmpty();
    }

    @Test
    void handle_returns_response_when_caller_is_admin_regardless_of_owner() {
        UUID ticketId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, customerId);
        when(ticketRepository.findByIdWithComments(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = handler.handle(new GetTicketQuery(ticketId, adminId, true));

        assertThat(response.customerId()).isEqualTo(customerId);
    }

    @Test
    void handle_throws_access_denied_when_non_admin_caller_does_not_own_ticket() {
        UUID ticketId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, customerId);
        when(ticketRepository.findByIdWithComments(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() ->
                handler.handle(new GetTicketQuery(ticketId, intruder, false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void handle_throws_resource_not_found_when_ticket_does_not_exist() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findByIdWithComments(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                handler.handle(new GetTicketQuery(unknownId, UUID.randomUUID(), true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    private static Ticket buildTicket(UUID id, UUID customerId) {
        Ticket t = Ticket.open(customerId, "BILLING", "HIGH", "Test subject",
                "billing-support", Instant.now().plusSeconds(3600));
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
