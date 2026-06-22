package com.telco.platform.common.exception;

import java.util.Map;

/** Raised when an authenticated principal lacks permission for the operation (HTTP 403). */
public final class AccessDeniedException extends PlatformException {

    public AccessDeniedException(String message) {
        super(CommonErrorCode.ACCESS_DENIED, message, null);
    }

    public AccessDeniedException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
