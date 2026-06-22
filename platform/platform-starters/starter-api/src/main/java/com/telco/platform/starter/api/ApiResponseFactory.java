package com.telco.platform.starter.api;

import com.telco.platform.common.api.ApiResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Builds successful {@link ApiResult} responses with {@link com.telco.platform.common.api.ApiMeta}
 * populated automatically from the active correlation context, service name, and request path
 * (ADR-015). Inject this into controllers so success responses stay one-liners:
 *
 * <pre>{@code
 * return responses.ok(dto);
 * }</pre>
 */
public final class ApiResponseFactory {

    private final ApiMetaFactory metaFactory;

    ApiResponseFactory(ApiMetaFactory metaFactory) {
        this.metaFactory = metaFactory;
    }

    /** Wraps {@code data} in a successful {@link ApiResult} with meta from the current request. */
    public <T> ApiResult<T> ok(T data) {
        return ApiResult.ok(data, metaFactory.create(currentPath()));
    }

    private static String currentPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRequestURI();
        }
        return null;
    }
}
