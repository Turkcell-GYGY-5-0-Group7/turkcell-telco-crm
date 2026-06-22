package com.telco.platform.mediator.behavior.support;

import java.time.Instant;

/**
 * Immutable record describing one handled request, forwarded to {@link RequestLogWriter}s.
 *
 * @param service       producing service name
 * @param requestType   request class simple name
 * @param requestKind   COMMAND, QUERY, or EVENT
 * @param userId        acting user id, if known
 * @param correlationId correlation id, if known
 * @param durationMs    handling duration in milliseconds
 * @param success       whether handling completed without error
 * @param errorCode     error code when not successful; null otherwise
 * @param timestamp     when the entry was produced
 */
public record RequestLogEntry(String service, String requestType, String requestKind,
                              String userId, String correlationId, long durationMs,
                              boolean success, String errorCode, Instant timestamp) {
}
