package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

public record AssignTicketCommand(UUID ticketId, String team) implements Command<Void> {}
