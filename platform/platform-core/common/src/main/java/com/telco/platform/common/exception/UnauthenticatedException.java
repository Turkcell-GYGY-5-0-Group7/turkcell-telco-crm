package com.telco.platform.common.exception;

import java.util.Map;

/** Raised when no valid authentication is present (HTTP 401). */
public final class UnauthenticatedException extends PlatformException {

    public UnauthenticatedException(String message) {
        super(CommonErrorCode.UNAUTHENTICATED, message, null);
    }

    public UnauthenticatedException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
