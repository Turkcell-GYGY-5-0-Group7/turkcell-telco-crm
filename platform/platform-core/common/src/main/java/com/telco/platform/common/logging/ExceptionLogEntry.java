package com.telco.platform.common.logging;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable description of a handled exception, suitable for persistence in non-production
 * (local/test) environments to ease debugging. In production, structured logs to Loki plus the
 * traceId/correlationId in {@code ApiResult.meta} remain the primary mechanism (ADR-012, ADR-015).
 *
 * @param id            generated id, surfaced to the client as {@code ApiError.logId}
 * @param service       originating service name
 * @param path          request path
 * @param exceptionType fully qualified exception class name
 * @param message       exception message
 * @param stackTrace    captured stack trace; may be truncated by the writer
 * @param statusCode    mapped HTTP status code
 * @param errorCode     mapped platform error code
 * @param traceId       distributed-trace id; may be null
 * @param correlationId request correlation id; may be null
 * @param userId        authenticated user id; may be null
 * @param timestamp     when the exception was handled
 */
public record ExceptionLogEntry(UUID id, String service, String path, String exceptionType,
                                String message, String stackTrace, int statusCode, String errorCode,
                                String traceId, String correlationId, String userId, Instant timestamp) {
}
