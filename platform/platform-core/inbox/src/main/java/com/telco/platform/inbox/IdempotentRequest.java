package com.telco.platform.inbox;

/**
 * Marker for requests guarded by the inbox: each carries a stable idempotency key so a duplicate
 * delivery is processed at most once.
 */
public interface IdempotentRequest {

    /** Stable, unique key identifying this logical message. */
    String idempotencyKey();
}
