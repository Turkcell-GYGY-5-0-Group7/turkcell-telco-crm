package com.telco.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Universal external response wrapper mandated by ADR-015. Either {@code data} (on success)
 * or {@code error} (on failure) is populated; {@code meta} carries observability context.
 *
 * @param <T> the success payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(boolean success, T data, ApiError error, ApiMeta meta) {

    /** Builds a successful result carrying the given payload and metadata. */
    public static <T> ApiResult<T> ok(T data, ApiMeta meta) {
        return new ApiResult<>(true, data, null, meta);
    }

    /** Builds a failed result carrying the given error and metadata. */
    public static <T> ApiResult<T> failure(ApiError error, ApiMeta meta) {
        return new ApiResult<>(false, null, error, meta);
    }
}
