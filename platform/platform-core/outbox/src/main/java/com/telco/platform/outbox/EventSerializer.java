package com.telco.platform.outbox;

/**
 * Serializes an event payload to its stored string form. Jackson impl lives in starter-outbox.
 */
public interface EventSerializer {

    /** Serializes the payload (typically to JSON). */
    String serialize(Object payload);

    /**
     * Serializes the payload and embeds {@code eventId} in it so downstream consumers have a stable,
     * unique key for inbox-based idempotency. The default ignores {@code eventId}; JSON-aware
     * implementations override it to inject the field.
     */
    default String serialize(Object payload, String eventId) {
        return serialize(payload);
    }
}
