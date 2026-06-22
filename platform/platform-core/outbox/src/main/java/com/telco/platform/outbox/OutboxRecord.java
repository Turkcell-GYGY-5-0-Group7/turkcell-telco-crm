package com.telco.platform.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * A transactional-outbox row. Written in the caller's transaction; Debezium captures the insert.
 *
 * @param id            unique row id
 * @param aggregateType aggregate the event belongs to (e.g. {@code customer})
 * @param aggregateId   aggregate instance id
 * @param eventType     event type, MUST follow {@code domain.event.v1}
 * @param payload       serialized event payload
 * @param headers       serialized headers; may be null
 * @param traceId       captured trace id; may be null
 * @param correlationId captured correlation id; may be null
 * @param createdAt     creation timestamp
 * @param status        delivery status
 * @param retryCount    number of failed delivery attempts; 0 for a fresh row
 * @param errorMessage  reason of the last failure; null while healthy
 */
public record OutboxRecord(UUID id, String aggregateType, String aggregateId, String eventType,
                           String payload, String headers, String traceId, String correlationId,
                           Instant createdAt, OutboxStatus status, int retryCount, String errorMessage) {

    /** Builds a fresh {@code NEW} row with no failures recorded yet. */
    public static OutboxRecord newRecord(UUID id, String aggregateType, String aggregateId, String eventType,
                                         String payload, String headers, String traceId, String correlationId,
                                         Instant createdAt) {
        return new OutboxRecord(id, aggregateType, aggregateId, eventType, payload, headers,
                traceId, correlationId, createdAt, OutboxStatus.NEW, 0, null);
    }
}
