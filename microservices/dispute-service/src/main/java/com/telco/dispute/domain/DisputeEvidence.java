package com.telco.dispute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A piece of evidence attached to a {@link Dispute}. Only the MinIO object reference is stored
 * here - raw bytes never live in {@code dispute-db} (design-note.md Section 7, ADR-006). The
 * upload/presigned-download flow is Feature 22.3's scope; this class is structural only.
 */
@Entity
@Table(name = "dispute_evidence")
public class DisputeEvidence {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Column(name = "submitted_by", nullable = false, length = 255)
    private String submittedBy;

    @Column(name = "object_ref", nullable = false, length = 500)
    private String objectRef;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** For JPA only. */
    protected DisputeEvidence() {
    }

    private DisputeEvidence(UUID id, Dispute dispute, String submittedBy, String objectRef, Instant submittedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.dispute = Objects.requireNonNull(dispute, "dispute");
        this.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
        this.objectRef = Objects.requireNonNull(objectRef, "objectRef");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    public static DisputeEvidence create(Dispute dispute, String submittedBy, String objectRef) {
        return new DisputeEvidence(UUID.randomUUID(), dispute, submittedBy, objectRef, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public String getObjectRef() {
        return objectRef;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
