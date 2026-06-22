package com.telco.platform.common.exception;

import java.util.Map;

/** Raised on a state conflict such as a duplicate or version mismatch (HTTP 409). */
public final class ConflictException extends PlatformException {

    public ConflictException(String message) {
        super(CommonErrorCode.CONFLICT, message, null);
    }

    public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
