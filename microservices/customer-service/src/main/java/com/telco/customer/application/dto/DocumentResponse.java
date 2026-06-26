package com.telco.customer.application.dto;

import com.telco.customer.domain.Document;

import java.time.Instant;
import java.util.UUID;

/** Read DTO for a stored KYC document. Exposes the object reference, never the binary. */
public record DocumentResponse(
        UUID id,
        UUID customerId,
        String type,
        String fileRef,
        String contentType,
        Instant verifiedAt,
        Instant createdAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getCustomerId(),
                document.getType().name(),
                document.getFileRef(),
                document.getContentType(),
                document.getVerifiedAt(),
                document.getCreatedAt()
        );
    }
}
