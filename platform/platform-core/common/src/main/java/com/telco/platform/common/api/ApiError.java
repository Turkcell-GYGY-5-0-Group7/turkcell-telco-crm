package com.telco.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Machine-readable error payload returned inside a failed {@link ApiResult}.
 *
 * @param code    stable error code (see {@code com.telco.platform.common.exception.ErrorCode})
 * @param message human-readable description, safe to surface to clients
 * @param details optional structured context (e.g. field violations); may be null or empty
 * @param traceId distributed-trace identifier for correlating logs
 * @param logId   id of the persisted exception-log row, when DB log persistence is enabled; else null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details, String traceId, String logId) {

    /** Builds an error without a persisted log id (the common case under Loki-only logging). */
    public ApiError(String code, String message, Map<String, Object> details, String traceId) {
        this(code, message, details, traceId, null);
    }
}
