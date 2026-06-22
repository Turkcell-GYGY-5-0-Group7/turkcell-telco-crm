package com.telco.platform.common.exception;

import java.util.Map;

/** Raised when a domain business rule is violated (HTTP 422). */
public final class BusinessRuleException extends PlatformException {

    public BusinessRuleException(String message) {
        super(CommonErrorCode.BUSINESS_RULE_VIOLATION, message, null);
    }

    public BusinessRuleException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
