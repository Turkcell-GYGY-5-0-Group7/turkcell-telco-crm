package com.telco.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response metadata attached to every {@link ApiResult} for observability and auditing.
 *
 * @param traceId       distributed-trace identifier
 * @param correlationId end-to-end correlation identifier
 * @param timestamp     server time the response was produced
 * @param service       logical name of the producing service
 * @param path          request path that produced this response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(String traceId, String correlationId, Instant timestamp, String service, String path) {
}
