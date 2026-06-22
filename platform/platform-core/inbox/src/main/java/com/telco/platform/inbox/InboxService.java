package com.telco.platform.inbox;

/**
 * Idempotency check for inbox-guarded message processing.
 */
public interface InboxService {

    /**
     * @return true if this is the first time {@code (messageId, handler)} is seen; false for a duplicate
     */
    boolean firstSeen(String messageId, String handler);
}
