package com.telco.platform.inbox;

import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.PipelineOrder;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotency guard for {@link IdempotentRequest}s. Runs at {@link PipelineOrder#INBOX} (inside the
 * transaction) so the inbox insert and the handler's writes commit together: if the message was
 * already processed, the handler is skipped (returns null); otherwise it runs and the row marks it done.
 */
public final class InboxBehavior implements PipelineBehavior {

    private static final Logger LOGGER = LoggerFactory.getLogger(InboxBehavior.class);

    private final InboxService inboxService;

    public InboxBehavior(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    public boolean supports(Object request) {
        return request instanceof IdempotentRequest;
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        IdempotentRequest idempotent = (IdempotentRequest) request;
        String key = idempotent.idempotencyKey();
        String handler = request.getClass().getName();
        if (!inboxService.firstSeen(key, handler)) {
            LOGGER.info("skipping already-processed message key={} handler={}", key, handler);
            return null;
        }
        return next.invoke();
    }

    @Override
    public int order() {
        return PipelineOrder.INBOX;
    }
}
