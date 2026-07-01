package com.telco.ticket.api.dto;

import com.telco.ticket.domain.TicketComment;
import java.time.Instant;
import java.util.UUID;

public record TicketCommentResponse(
        UUID id,
        UUID authorId,
        String body,
        Instant createdAt
) {
    public static TicketCommentResponse from(TicketComment c) {
        return new TicketCommentResponse(c.getId(), c.getAuthorId(), c.getBody(), c.getCreatedAt());
    }
}
