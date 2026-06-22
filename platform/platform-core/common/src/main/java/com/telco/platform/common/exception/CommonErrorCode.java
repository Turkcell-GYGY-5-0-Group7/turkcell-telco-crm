package com.telco.platform.common.exception;

/**
 * Platform-wide baseline error codes shared by all services.
 */
public enum CommonErrorCode implements ErrorCode {

    RESOURCE_NOT_FOUND,
    VALIDATION_FAILED,
    CONFLICT,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    BUSINESS_RULE_VIOLATION,
    DEPENDENCY_FAILURE,
    INTERNAL_ERROR;

    @Override
    public String code() {
        return name();
    }
}
