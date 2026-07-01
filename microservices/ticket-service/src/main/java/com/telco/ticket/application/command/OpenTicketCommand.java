package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

public record OpenTicketCommand(
        UUID customerId,
        String category,
        String priority,
        String subject
) implements Command<UUID> {}
