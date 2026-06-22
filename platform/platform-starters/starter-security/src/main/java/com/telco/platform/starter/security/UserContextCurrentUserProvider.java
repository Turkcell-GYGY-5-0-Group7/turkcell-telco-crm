package com.telco.platform.starter.security;

import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.context.UserContextHolder;

/**
 * {@link CurrentUserProvider} that reads the principal bound by {@link JwtAuthFilter}.
 * Falls back to the anonymous context when no user is bound to the current thread.
 */
public final class UserContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UserContext currentUser() {
        return UserContextHolder.get().orElseGet(UserContext::anonymous);
    }
}
