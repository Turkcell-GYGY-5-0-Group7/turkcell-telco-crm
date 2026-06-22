package com.telco.platform.inbox;

/**
 * Persistence port recording processed message ids per handler. JDBC impl lives in starter-inbox.
 */
public interface InboxStore {

    /**
     * Records {@code (messageId, handler)} as processed.
     *
     * @return true if newly inserted (first time seen); false if it was already present (duplicate)
     */
    boolean markProcessed(String messageId, String handler);
}
