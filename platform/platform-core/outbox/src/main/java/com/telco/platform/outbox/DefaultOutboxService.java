package com.telco.platform.outbox;

import com.telco.platform.common.context.CorrelationContext;
import com.telco.platform.common.context.CorrelationContextHolder;

import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link OutboxService}: serializes the payload, captures trace/correlation from
 * {@link CorrelationContextHolder}, and appends the row in the caller's transaction so the domain
 * write and the outbox insert commit atomically (Debezium then captures the insert).
 */
public final class DefaultOutboxService implements OutboxService {

    private final OutboxStore store;
    private final EventSerializer serializer;

    public DefaultOutboxService(OutboxStore store, EventSerializer serializer) {
        this.store = store;
        this.serializer = serializer;
    }

    @Override
    public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        CorrelationContext correlation = CorrelationContextHolder.get().orElse(null);
        String traceId = correlation == null ? null : correlation.traceId();
        String correlationId = correlation == null ? null : correlation.correlationId();
        UUID eventId = UUID.randomUUID();
        OutboxRecord record = OutboxRecord.newRecord(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                serializer.serialize(payload, eventId.toString()),
                null,
                traceId,
                correlationId,
                Instant.now());
        store.append(record);
    }
}
