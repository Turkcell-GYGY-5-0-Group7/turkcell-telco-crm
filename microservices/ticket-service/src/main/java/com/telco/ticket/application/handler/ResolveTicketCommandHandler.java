package com.telco.ticket.application.handler;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.ResolveTicketCommand;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Component
public class ResolveTicketCommandHandler implements CommandHandler<ResolveTicketCommand, Void> {

    private static final String AGGREGATE_TYPE = "ticket";

    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;

    public ResolveTicketCommandHandler(TicketRepository ticketRepository, OutboxService outboxService) {
        this.ticketRepository = ticketRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Void handle(ResolveTicketCommand command) {
        Ticket ticket = ticketRepository.findById(command.ticketId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + command.ticketId()));
        if (ticket.getStatus().name().equals("RESOLVED")) return null;

        ticket.resolve();
        ticketRepository.save(ticket);

        outboxService.publish(AGGREGATE_TYPE, ticket.getId().toString(), "ticket.resolved.v1",
                Map.of("ticketId", ticket.getId().toString(),
                        "customerId", ticket.getCustomerId().toString(),
                        "resolvedAt", ticket.getResolvedAt().toString()));
        return null;
    }
}
