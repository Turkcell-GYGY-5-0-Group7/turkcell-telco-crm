package com.telco.dispute.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.dispute.domain.AuditLog;
import com.telco.dispute.infrastructure.persistence.AuditLogRepository;
import com.telco.platform.common.context.CorrelationContextHolder;
import com.telco.platform.common.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Writes one audit_log row per state-changing operation; runs inside the caller's transaction (NFR-12). */
@Component
public class AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriter.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogWriter(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists an audit record for the given action. Runs in the caller's transaction; does not open
     * its own.
     *
     * @param action   action code, e.g. {@code "DISPUTE_OPENED"}
     * @param entity   entity type name, e.g. {@code "Dispute"}
     * @param entityId string representation of the entity's primary key; may be null
     * @param details  arbitrary key-value pairs serialized to JSON; may be null. MUST NOT contain PII.
     */
    public void log(String action, String entity, String entityId, Map<String, Object> details) {
        String rawActorId = UserContextHolder.get().map(u -> u.userId()).orElse(null);
        UUID actorId = null;
        if (rawActorId != null) {
            try {
                actorId = UUID.fromString(rawActorId);
            } catch (IllegalArgumentException ignored) {
                // Non-UUID principal (service accounts, test principals) - actor_id left null
            }
        }

        String correlationId = CorrelationContextHolder.get().map(c -> c.correlationId()).orElse(null);

        String detailsJson = null;
        if (details != null) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                log.warn("audit details serialization failed for action={} entity={}", action, entity, e);
            }
        }

        AuditLog entry = new AuditLog(
                UUID.randomUUID(),
                actorId,
                action,
                entity,
                entityId,
                detailsJson,
                correlationId,
                Instant.now()
        );

        auditLogRepository.save(entry);
    }
}
