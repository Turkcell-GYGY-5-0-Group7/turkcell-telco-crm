package com.telco.customer.application.handler;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.UploadDocumentCommand;
import com.telco.customer.application.dto.DocumentResponse;
import com.telco.customer.domain.Document;
import com.telco.customer.infrastructure.CustomerRepository;
import com.telco.customer.infrastructure.DocumentRepository;
import com.telco.customer.infrastructure.storage.DocumentStorage;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.common.exception.ValidationException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and stores a KYC document: the binary goes to MinIO and only the object reference is
 * persisted (ADR-006, FR-03, AC-01 step 2). An audit row is written.
 */
@Component
public class UploadDocumentCommandHandler
        implements CommandHandler<UploadDocumentCommand, DocumentResponse> {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "application/pdf");

    private final CustomerRepository customers;
    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final AuditLogWriter audit;

    public UploadDocumentCommandHandler(CustomerRepository customers, DocumentRepository documents,
                                        DocumentStorage storage, AuditLogWriter audit) {
        this.customers = customers;
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
    }

    @Override
    public DocumentResponse handle(UploadDocumentCommand command) {
        if (!customers.existsById(command.customerId())) {
            throw new ResourceNotFoundException("customer not found: " + command.customerId());
        }
        validate(command);

        String checksum = sha256(command.content());
        String objectKey = "customers/" + command.customerId() + "/" + UUID.randomUUID()
                + "-" + sanitize(command.filename());

        storage.store(objectKey, command.content(), command.contentType());

        Document document = documents.save(Document.record(
                command.customerId(), command.type(), objectKey, command.contentType(), checksum));

        audit.log("DOCUMENT_UPLOADED", "Document", document.getId().toString(),
                Map.of("customerId", command.customerId().toString(), "type", command.type().name()));

        return DocumentResponse.from(document);
    }

    private void validate(UploadDocumentCommand command) {
        if (command.content() == null || command.content().length == 0) {
            throw new ValidationException("document content is empty", Map.of());
        }
        if (command.content().length > MAX_SIZE_BYTES) {
            throw new ValidationException("document exceeds the maximum size",
                    Map.of("maxBytes", MAX_SIZE_BYTES, "actualBytes", command.content().length));
        }
        if (command.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(command.contentType())) {
            throw new ValidationException("unsupported document content type",
                    Map.of("allowed", ALLOWED_CONTENT_TYPES));
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
