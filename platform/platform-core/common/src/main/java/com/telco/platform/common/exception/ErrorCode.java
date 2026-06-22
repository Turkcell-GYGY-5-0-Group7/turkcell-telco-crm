package com.telco.platform.common.exception;

/**
 * A stable, machine-readable error code. Implemented by {@link CommonErrorCode} and by
 * service-specific code enums.
 */
public interface ErrorCode {

    /** The stable error code string (e.g. {@code RESOURCE_NOT_FOUND}). */
    String code();
}
