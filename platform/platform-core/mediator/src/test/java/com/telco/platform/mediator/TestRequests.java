package com.telco.platform.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.Query;

/** Shared test request/event types for mediator tests. */
final class TestRequests {

    private TestRequests() {
    }

    record SampleCommand(String value) implements Command<String> {
    }

    record SampleQuery(String value) implements Query<String> {
    }

    record SampleEvent(String value) implements Event {
    }
}
