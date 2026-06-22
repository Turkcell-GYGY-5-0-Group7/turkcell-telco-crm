package com.telco.platform.inbox;

/**
 * Default {@link InboxService} delegating idempotency bookkeeping to an {@link InboxStore}.
 */
public final class DefaultInboxService implements InboxService {

    private final InboxStore store;

    public DefaultInboxService(InboxStore store) {
        this.store = store;
    }

    @Override
    public boolean firstSeen(String messageId, String handler) {
        return store.markProcessed(messageId, handler);
    }
}
