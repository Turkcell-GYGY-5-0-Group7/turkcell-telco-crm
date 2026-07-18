package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

public record OpenTicketCommand(
        UUID customerId,
        String category,
        String priority,
        String subject,
        String externalRef
) implements Command<UUID> {

    /**
     * Agent/API-opened ticket with no cross-context source reference. Preserves the original
     * four-argument call sites (controller, existing tests); the fraud auto-ticket consumer uses the
     * five-argument form to carry the originating fraud {@code caseId} as {@code externalRef}.
     */
    public OpenTicketCommand(UUID customerId, String category, String priority, String subject) {
        this(customerId, category, priority, subject, null);
    }
}
