package com.telco.platform.lock;

import com.telco.platform.common.exception.ErrorCode;

/**
 * Error codes for the distributed-lock capability. Used to construct the platform's existing
 * {@code DependencyFailureException} on fail-closed acquisition failure (ADR-024 Section 5);
 * {@code PlatformException} is sealed to {@code com.telco.platform.common.exception}, so this
 * module cannot define its own subtype and instead carries a distinguishing code + details.
 */
public enum LockErrorCode implements ErrorCode {

    LOCK_ACQUISITION_FAILED;

    @Override
    public String code() {
        return name();
    }
}
