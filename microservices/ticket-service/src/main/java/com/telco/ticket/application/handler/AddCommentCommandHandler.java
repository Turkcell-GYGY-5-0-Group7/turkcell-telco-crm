package com.telco.ticket.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.ticket.application.command.AddCommentCommand;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.domain.TicketComment;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class AddCommentCommandHandler implements CommandHandler<AddCommentCommand, UUID> {

    private final TicketRepository ticketRepository;

    public AddCommentCommandHandler(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional
    public UUID handle(AddCommentCommand command) {
        Ticket ticket = ticketRepository.findByIdWithComments(command.ticketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + command.ticketId()));
        TicketComment comment = ticket.addComment(command.authorId(), command.body());
        ticketRepository.save(ticket);
        return comment.getId();
    }
}
