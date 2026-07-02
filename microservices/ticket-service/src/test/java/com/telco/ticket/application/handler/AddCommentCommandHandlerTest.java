package com.telco.ticket.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.ticket.application.command.AddCommentCommand;
import com.telco.ticket.domain.Ticket;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddCommentCommandHandlerTest {

    @Mock private TicketRepository ticketRepository;

    private AddCommentCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddCommentCommandHandler(ticketRepository);
    }

    @Test
    void handle_adds_comment_to_ticket_and_saves() {
        UUID ticketId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Ticket ticket = buildTicket(ticketId, UUID.randomUUID());
        when(ticketRepository.findByIdWithComments(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);

        handler.handle(new AddCommentCommand(ticketId, authorId, "We are investigating."));

        assertThat(ticket.getComments()).hasSize(1);
        assertThat(ticket.getComments().get(0).getBody()).isEqualTo("We are investigating.");
        assertThat(ticket.getComments().get(0).getAuthorId()).isEqualTo(authorId);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void handle_throws_resource_not_found_when_ticket_does_not_exist() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findByIdWithComments(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                handler.handle(new AddCommentCommand(unknownId, UUID.randomUUID(), "body")))
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
