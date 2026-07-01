package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

public record ResolveTicketCommand(UUID ticketId) implements Command<Void> {}
