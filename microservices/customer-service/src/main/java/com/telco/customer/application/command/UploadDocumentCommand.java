package com.telco.customer.application.command;

import com.telco.customer.application.dto.DocumentResponse;
import com.telco.customer.domain.DocumentType;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Uploads a KYC document: stores the binary in MinIO and records the reference (FR-03, AC-01). */
public record UploadDocumentCommand(
        UUID customerId,
        DocumentType type,
        String filename,
        String contentType,
        byte[] content
) implements Command<DocumentResponse> {
}
