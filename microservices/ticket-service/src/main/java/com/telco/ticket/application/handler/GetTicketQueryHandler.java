package com.telco.ticket.application.handler;

import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.ticket.api.dto.TicketResponse;
import com.telco.ticket.application.query.GetTicketQuery;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetTicketQueryHandler implements QueryHandler<GetTicketQuery, TicketResponse> {

    private final TicketRepository ticketRepository;

    public GetTicketQueryHandler(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse handle(GetTicketQuery query) {
        Ticket ticket = ticketRepository.findByIdWithComments(query.ticketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + query.ticketId()));

        if (!query.callerIsAdmin()
                && (query.callerCustomerId() == null
                        || !query.callerCustomerId().equals(ticket.getCustomerId().toString()))) {
            throw new AccessDeniedException("Access denied to ticket " + query.ticketId());
        }
        return TicketResponse.from(ticket);
    }
}
