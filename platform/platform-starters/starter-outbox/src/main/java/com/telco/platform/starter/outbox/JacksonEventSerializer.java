package com.telco.platform.starter.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telco.platform.outbox.EventSerializer;

/**
 * Jackson-backed {@link EventSerializer} producing JSON for the outbox payload column.
 *
 * <p>When an {@code eventId} is supplied, it is embedded as a top-level field in the JSON so a
 * consumer reading the event from Kafka has a stable, unique key for inbox idempotency.
 */
public final class JacksonEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;

    public JacksonEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize outbox payload: " + e.getMessage(), e);
        }
    }

    @Override
    public String serialize(Object payload, String eventId) {
        try {
            ObjectNode node = payload instanceof String json
                    ? (ObjectNode) objectMapper.readTree(json)
                    : objectMapper.valueToTree(payload);
            node.put("eventId", eventId);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize outbox payload: " + e.getMessage(), e);
        }
    }
}
