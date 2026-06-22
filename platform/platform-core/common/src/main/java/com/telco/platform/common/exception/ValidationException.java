package com.telco.platform.common.exception;

import java.util.Map;

/** Raised when request validation fails; field violations live in {@code details} (HTTP 400). */
public final class ValidationException extends PlatformException {

    public ValidationException(String message, Map<String, Object> details) {
        super(CommonErrorCode.VALIDATION_FAILED, message, details);
    }

    public ValidationException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
