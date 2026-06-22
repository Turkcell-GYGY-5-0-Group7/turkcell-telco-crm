package com.telco.platform.outbox;

import com.telco.platform.common.context.CorrelationContext;
import com.telco.platform.common.context.CorrelationContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultOutboxServiceTest {

    @AfterEach
    void clear() {
        CorrelationContextHolder.clear();
    }

    @Test
    void buildsRecordCapturingCorrelationAndSerializedPayload() {
        List<OutboxRecord> appended = new ArrayList<>();
        OutboxStore store = recordingStore(appended);
        EventSerializer serializer = payload -> "json:" + payload;
        CorrelationContextHolder.set(new CorrelationContext("trace-1", "corr-1"));

        DefaultOutboxService service = new DefaultOutboxService(store, serializer);
        service.publish("customer", "c-42", "customer.registered.v1", "PAYLOAD");

        assertEquals(1, appended.size());
        OutboxRecord record = appended.get(0);
        assertNotNull(record.id());
        assertEquals("customer", record.aggregateType());
        assertEquals("c-42", record.aggregateId());
        assertEquals("customer.registered.v1", record.eventType());
        assertEquals("json:PAYLOAD", record.payload());
        assertEquals("trace-1", record.traceId());
        assertEquals("corr-1", record.correlationId());
        assertEquals(OutboxStatus.NEW, record.status());
        assertNotNull(record.createdAt());
        assertEquals(0, record.retryCount());
        assertNull(record.errorMessage());
    }

    @Test
    void leavesCorrelationNullWhenNoneBound() {
        List<OutboxRecord> appended = new ArrayList<>();
        DefaultOutboxService service = new DefaultOutboxService(recordingStore(appended), o -> "x");
        service.publish("order", "o-1", "order.created.v1", new Object());

        OutboxRecord record = appended.get(0);
        assertNull(record.traceId());
        assertNull(record.correlationId());
    }

    private OutboxStore recordingStore(List<OutboxRecord> sink) {
        return new OutboxStore() {
            @Override
            public void append(OutboxRecord record) {
                sink.add(record);
            }

            @Override
            public List<OutboxRecord> findByStatus(OutboxStatus status, int limit) {
                return List.of();
            }

            @Override
            public int countByStatusOlderThan(OutboxStatus status, java.time.Instant olderThan) {
                return 0;
            }

            @Override
            public void markPublished(UUID id) {
            }

            @Override
            public void markFailed(UUID id, String errorMessage) {
            }
        };
    }
}
