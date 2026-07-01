package com.telco.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private String team;

    @Column(nullable = false)
    private int resolutionMinutes;

    protected SlaPolicy() {}

    public UUID getId() { return id; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public String getTeam() { return team; }
    public int getResolutionMinutes() { return resolutionMinutes; }
}
