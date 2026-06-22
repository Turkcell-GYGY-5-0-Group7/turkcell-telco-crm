package com.telco.platform.common.exception;

import java.util.Map;

/** Raised when a requested resource does not exist (maps to HTTP 404 in starter-api). */
public final class ResourceNotFoundException extends PlatformException {

    public ResourceNotFoundException(String message) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, message, null);
    }

    public ResourceNotFoundException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
