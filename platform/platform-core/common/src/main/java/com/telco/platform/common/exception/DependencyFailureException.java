package com.telco.platform.common.exception;

import java.util.Map;

/** Raised when a downstream dependency fails or is unavailable (HTTP 502/503). */
public final class DependencyFailureException extends PlatformException {

    public DependencyFailureException(String message, Throwable cause) {
        super(CommonErrorCode.DEPENDENCY_FAILURE, message, null, cause);
    }

    public DependencyFailureException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(code, message, details, cause);
    }
}
