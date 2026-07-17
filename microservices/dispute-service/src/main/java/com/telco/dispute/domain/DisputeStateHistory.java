package com.telco.dispute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One row per successful {@link Dispute} state transition - the audit trail a defensible dispute
 * resolution requires (design-note.md Section 7). Appended exclusively by {@link Dispute}'s own
 * transition methods; never constructed directly by application code.
 */
@Entity
@Table(name = "dispute_state_history")
public class DisputeStateHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private DisputeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private DisputeStatus toStatus;

    @Column(name = "changed_by", length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "note", length = 1000)
    private String note;

    /** For JPA only. */
    protected DisputeStateHistory() {
    }

    private DisputeStateHistory(UUID id, Dispute dispute, DisputeStatus fromStatus, DisputeStatus toStatus,
                                String changedBy, Instant changedAt, String note) {
        this.id = Objects.requireNonNull(id, "id");
        this.dispute = Objects.requireNonNull(dispute, "dispute");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.changedBy = changedBy;
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
        this.note = note;
    }

    static DisputeStateHistory create(Dispute dispute, DisputeStatus fromStatus, DisputeStatus toStatus,
                                      String changedBy, String note) {
        return new DisputeStateHistory(UUID.randomUUID(), dispute, fromStatus, toStatus, changedBy,
                Instant.now(), note);
    }

    public UUID getId() {
        return id;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public DisputeStatus getFromStatus() {
        return fromStatus;
    }

    public DisputeStatus getToStatus() {
        return toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getNote() {
        return note;
    }
}
