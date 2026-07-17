package com.telco.ticket.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    private String assignedTeam;

    @Column(nullable = false)
    private String subject;

    private Instant slaDueAt;

    private Instant slaBreachedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant resolvedAt;

    @Column(name = "external_ref")
    private String externalRef;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TicketComment> comments = new ArrayList<>();

    protected Ticket() {}

    /** Original 6-arg form, preserved for every existing (non-dispute) caller - externalRef defaults to null. */
    public static Ticket open(UUID customerId, String category, String priority, String subject,
                               String assignedTeam, Instant slaDueAt) {
        return open(customerId, category, priority, subject, assignedTeam, slaDueAt, null);
    }

    public static Ticket open(UUID customerId, String category, String priority, String subject,
                               String assignedTeam, Instant slaDueAt, String externalRef) {
        var t = new Ticket();
        t.customerId = customerId;
        t.category = category;
        t.priority = priority;
        t.subject = subject;
        t.status = TicketStatus.OPEN;
        t.assignedTeam = assignedTeam;
        t.slaDueAt = slaDueAt;
        t.externalRef = externalRef;
        t.createdAt = Instant.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    public void assign(String team) {
        this.assignedTeam = team;
        this.status = TicketStatus.ASSIGNED;
        this.updatedAt = Instant.now();
    }

    public void resolve() {
        this.status = TicketStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.updatedAt = this.resolvedAt;
    }

    public void markSlaBreached() {
        this.slaBreachedAt = Instant.now();
        this.updatedAt = this.slaBreachedAt;
    }

    public TicketComment addComment(UUID authorId, String body) {
        var comment = TicketComment.of(this, authorId, body);
        comments.add(comment);
        return comment;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public String getAssignedTeam() { return assignedTeam; }
    public String getSubject() { return subject; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getSlaBreachedAt() { return slaBreachedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getExternalRef() { return externalRef; }
    public List<TicketComment> getComments() { return Collections.unmodifiableList(comments); }
}
