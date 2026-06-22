package com.telco.platform.mediator.behavior;

import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.mediator.behavior.support.AuthorizationRule;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.PipelineOrder;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;

import java.util.List;

/**
 * Applies each {@link AuthorizationRule} that supports the request against the current user before
 * the handler runs. Rules raise {@code AccessDeniedException}/{@code UnauthenticatedException}.
 */
public final class AuthorizationBehavior implements PipelineBehavior {

    private final CurrentUserProvider currentUserProvider;
    private final List<AuthorizationRule> rules;

    public AuthorizationBehavior(CurrentUserProvider currentUserProvider, List<AuthorizationRule> rules) {
        this.currentUserProvider = currentUserProvider;
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean supports(Object request) {
        for (AuthorizationRule rule : rules) {
            if (rule.supports(request)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        UserContext user = currentUserProvider.currentUser();
        for (AuthorizationRule rule : rules) {
            if (rule.supports(request)) {
                rule.check(request, user);
            }
        }
        return next.invoke();
    }

    @Override
    public int order() {
        return PipelineOrder.AUTHORIZATION;
    }
}
