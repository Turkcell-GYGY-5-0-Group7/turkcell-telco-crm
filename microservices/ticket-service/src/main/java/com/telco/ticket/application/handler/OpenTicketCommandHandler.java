package com.telco.ticket.application.handler;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.ticket.application.command.OpenTicketCommand;
import com.telco.ticket.domain.SlaPolicy;
import com.telco.ticket.domain.Ticket;
import com.telco.ticket.infrastructure.persistence.SlaPolicyRepository;
import com.telco.ticket.infrastructure.persistence.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenTicketCommandHandler implements CommandHandler<OpenTicketCommand, UUID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTicketCommandHandler.class);
    private static final String AGGREGATE_TYPE = "ticket";

    private final TicketRepository ticketRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final OutboxService outboxService;

    public OpenTicketCommandHandler(TicketRepository ticketRepository,
                                    SlaPolicyRepository slaPolicyRepository,
                                    OutboxService outboxService) {
        this.ticketRepository = ticketRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public UUID handle(OpenTicketCommand command) {
        String cat = command.category().toUpperCase();
        String pri = command.priority().toUpperCase();

        String team = slaPolicyRepository.findByCategoryAndPriority(cat, pri)
                .map(SlaPolicy::getTeam).orElse("customer-care");
        int minutes = slaPolicyRepository.findByCategoryAndPriority(cat, pri)
                .map(SlaPolicy::getResolutionMinutes).orElse(1440);

        Instant slaDueAt = Instant.now().plus(minutes, ChronoUnit.MINUTES);

        Ticket ticket = Ticket.open(command.customerId(), cat, pri, command.subject(), team, slaDueAt);
        ticket = ticketRepository.save(ticket);

        outboxService.publish(AGGREGATE_TYPE, ticket.getId().toString(), "ticket.opened.v1",
                Map.of("ticketId", ticket.getId().toString(),
                        "customerId", ticket.getCustomerId().toString(),
                        "category", ticket.getCategory(),
                        "priority", ticket.getPriority(),
                        "subject", ticket.getSubject(),
                        "assignedTeam", ticket.getAssignedTeam(),
                        "slaDueAt", ticket.getSlaDueAt().toString()));

        outboxService.publish(AGGREGATE_TYPE, ticket.getId().toString(), "ticket.assigned.v1",
                Map.of("ticketId", ticket.getId().toString(),
                        "customerId", ticket.getCustomerId().toString(),
                        "assignedTeam", ticket.getAssignedTeam()));

        LOGGER.info("Ticket opened id={} customerId={} category={} team={} slaDueAt={}",
                ticket.getId(), command.customerId(), cat, team, slaDueAt);
        return ticket.getId();
    }
}
