package com.telco.platform.outbox;

/**
 * Write-side API for publishing domain events through the transactional outbox.
 */
public interface OutboxService {

    /**
     * Serializes {@code payload} and appends an outbox row in the caller's transaction.
     *
     * @param eventType MUST follow {@code domain.event.v1}
     */
    void publish(String aggregateType, String aggregateId, String eventType, Object payload);
}
