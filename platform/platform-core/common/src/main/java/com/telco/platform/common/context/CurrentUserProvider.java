package com.telco.platform.common.context;

/**
 * Supplies the current {@link UserContext}. The default returns the anonymous context;
 * starter-security overrides it to read from the active security/user context.
 */
public interface CurrentUserProvider {

    /** Returns the current user, never null. */
    default UserContext currentUser() {
        return UserContext.anonymous();
    }
}
