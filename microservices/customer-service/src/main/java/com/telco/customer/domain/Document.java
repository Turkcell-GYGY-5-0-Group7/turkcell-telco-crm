package com.telco.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A KYC identity document for a customer (FR-03, AC-01 step 2). The binary lives in MinIO; this row
 * holds only the object reference ({@code fileRef}) plus content metadata (ADR-006). Raw bytes are
 * never stored in the database.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentType type;

    @Column(name = "file_ref", nullable = false, length = 512)
    private String fileRef;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(length = 128)
    private String checksum;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Document() {
        // for JPA
    }

    public Document(UUID id, UUID customerId, DocumentType type, String fileRef, String contentType,
                    String checksum, Instant verifiedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.type = Objects.requireNonNull(type, "type");
        this.fileRef = Objects.requireNonNull(fileRef, "fileRef");
        this.contentType = contentType;
        this.checksum = checksum;
        this.verifiedAt = verifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Records an uploaded KYC document, referencing the stored MinIO object. */
    public static Document record(UUID customerId, DocumentType type, String fileRef,
                                  String contentType, String checksum) {
        return new Document(UUID.randomUUID(), customerId, type, fileRef, contentType, checksum, null,
                Instant.now());
    }

    public void markVerified() {
        this.verifiedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public DocumentType getType() {
        return type;
    }

    public String getFileRef() {
        return fileRef;
    }

    public String getContentType() {
        return contentType;
    }

    public String getChecksum() {
        return checksum;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
