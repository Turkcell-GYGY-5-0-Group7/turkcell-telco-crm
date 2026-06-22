package com.telco.platform.mediator.behavior.support;

import com.telco.platform.common.context.UserContext;

/**
 * A single authorization check applied to matching requests by {@code AuthorizationBehavior}.
 */
public interface AuthorizationRule {

    /** Whether this rule applies to the given request. */
    boolean supports(Object request);

    /**
     * Verifies the user may execute the request; throws
     * {@code AccessDeniedException}/{@code UnauthenticatedException} on failure.
     */
    void check(Object request, UserContext user);
}
