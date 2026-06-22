package com.telco.platform.mediator.behavior.support;

/**
 * Sink for {@link RequestLogEntry} records. A DB-backed writer may be added by a starter.
 */
public interface RequestLogWriter {

    /** Persists or emits the given log entry. */
    void write(RequestLogEntry entry);
}
