package com.telco.ticket.application.handler;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.DetectSlaBreachCommand;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class DetectSlaBreachCommandHandler implements CommandHandler<DetectSlaBreachCommand, Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectSlaBreachCommandHandler.class);
    private static final String AGGREGATE_TYPE = "ticket";

    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;

    public DetectSlaBreachCommandHandler(TicketRepository ticketRepository, OutboxService outboxService) {
        this.ticketRepository = ticketRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public Integer handle(DetectSlaBreachCommand command) {
        List<Ticket> breached = ticketRepository.findBreached(Instant.now());
        for (Ticket ticket : breached) {
            outboxService.publish(AGGREGATE_TYPE, ticket.getId().toString(), "ticket.sla-breached.v1",
                    Map.of("ticketId", ticket.getId().toString(),
                            "customerId", ticket.getCustomerId().toString(),
                            "category", ticket.getCategory(),
                            "priority", ticket.getPriority(),
                            "slaDueAt", ticket.getSlaDueAt().toString()));
            // Mark the breach so a subsequent scheduler run does not re-emit for this ticket (12.5.3: emit once).
            ticket.markSlaBreached();
            ticketRepository.save(ticket);
        }
        if (!breached.isEmpty()) {
            LOGGER.warn("SLA breach detected for {} tickets", breached.size());
        }
        return breached.size();
    }
}
