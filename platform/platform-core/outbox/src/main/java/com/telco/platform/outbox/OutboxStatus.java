package com.telco.platform.outbox;

/**
 * Lifecycle status of an outbox row.
 */
public enum OutboxStatus {
    NEW,
    PUBLISHED,
    FAILED
}
