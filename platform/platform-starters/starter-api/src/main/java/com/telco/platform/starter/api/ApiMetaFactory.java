package com.telco.platform.starter.api;

import com.telco.platform.common.api.ApiMeta;
import com.telco.platform.common.context.CorrelationContextHolder;

import java.time.Instant;

/**
 * Builds {@link ApiMeta} for outgoing responses from the active correlation context (ADR-015).
 *
 * <p>Reads traceId/correlationId from {@link CorrelationContextHolder} (populated by
 * starter-observability) and stamps the service name, timestamp, and request path.
 */
final class ApiMetaFactory {

    private final String serviceName;

    ApiMetaFactory(String serviceName) {
        this.serviceName = serviceName;
    }

    ApiMeta create(String path) {
        String traceId = CorrelationContextHolder.get()
                .map(c -> c.traceId())
                .orElse(null);
        String correlationId = CorrelationContextHolder.get()
                .map(c -> c.correlationId())
                .orElse(null);
        return new ApiMeta(traceId, correlationId, Instant.now(), serviceName, path);
    }

    String traceId() {
        return CorrelationContextHolder.get().map(c -> c.traceId()).orElse(null);
    }
}
