package com.telco.platform.common.exception;

import java.util.Map;

/**
 * Sealed base type for all platform domain exceptions. Each subtype carries an {@link ErrorCode}
 * and optional structured {@code details}; the HTTP status mapping lives in starter-api, not here.
 */
public sealed class PlatformException extends RuntimeException
        permits ResourceNotFoundException, ValidationException, ConflictException,
                UnauthenticatedException, AccessDeniedException, BusinessRuleException,
                DependencyFailureException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    protected PlatformException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
    }

    protected PlatformException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    /** The stable error code for this exception. */
    public ErrorCode code() {
        return code;
    }

    /** Optional structured context; may be null. */
    public Map<String, Object> details() {
        return details;
    }
}
