package com.telco.billing.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bill_cycles")
public class BillCycle {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BillCycle() {}

    public static BillCycle create(UUID customerId, int dayOfMonth, LocalDate nextRunDate) {
        BillCycle cycle = new BillCycle();
        cycle.id = UUID.randomUUID();
        cycle.customerId = customerId;
        cycle.dayOfMonth = dayOfMonth;
        cycle.nextRunDate = nextRunDate;
        cycle.createdAt = Instant.now();
        return cycle;
    }

    public void advance(LocalDate newNextRunDate) {
        this.nextRunDate = newNextRunDate;
    }

    public UUID getId()             { return id; }
    public UUID getCustomerId()     { return customerId; }
    public int getDayOfMonth()      { return dayOfMonth; }
    public LocalDate getNextRunDate() { return nextRunDate; }
    public Instant getCreatedAt()   { return createdAt; }
}
