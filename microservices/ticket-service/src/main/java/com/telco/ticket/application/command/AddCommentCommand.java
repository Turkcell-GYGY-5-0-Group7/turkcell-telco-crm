package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

public record AddCommentCommand(
        UUID ticketId,
        UUID authorId,
        String body
) implements Command<UUID> {}
