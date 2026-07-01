package com.telco.billing.infrastructure.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "overage_records")
public class OverageRecord {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "voice_overage_seconds", nullable = false)
    private long voiceOverageSeconds;

    @Column(name = "sms_overage_count", nullable = false)
    private long smsOverageCount;

    @Column(name = "data_overage_kb", nullable = false)
    private long dataOverageKb;

    @Column(name = "aggregated_at", nullable = false)
    private Instant aggregatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OverageRecord() {}

    public static OverageRecord from(UUID subscriptionId, Instant periodStart, Instant periodEnd,
                                     long voiceOverageSeconds, long smsOverageCount, long dataOverageKb,
                                     Instant aggregatedAt) {
        OverageRecord r = new OverageRecord();
        r.id = UUID.randomUUID();
        r.subscriptionId = subscriptionId;
        r.periodStart = periodStart;
        r.periodEnd = periodEnd;
        r.voiceOverageSeconds = voiceOverageSeconds;
        r.smsOverageCount = smsOverageCount;
        r.dataOverageKb = dataOverageKb;
        r.aggregatedAt = aggregatedAt;
        r.createdAt = Instant.now();
        return r;
    }

    public UUID getId()                  { return id; }
    public UUID getSubscriptionId()      { return subscriptionId; }
    public Instant getPeriodStart()      { return periodStart; }
    public Instant getPeriodEnd()        { return periodEnd; }
    public long getVoiceOverageSeconds() { return voiceOverageSeconds; }
    public long getSmsOverageCount()     { return smsOverageCount; }
    public long getDataOverageKb()       { return dataOverageKb; }
    public Instant getAggregatedAt()     { return aggregatedAt; }
    public Instant getCreatedAt()        { return createdAt; }
}
