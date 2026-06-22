package com.telco.platform.inbox;

import com.telco.platform.cqrs.Command;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InboxBehaviorTest {

    record GuardedCommand(String key) implements Command<String>, IdempotentRequest {
        @Override
        public String idempotencyKey() {
            return key;
        }
    }

    record PlainCommand(String value) implements Command<String> {
    }

    /** In-memory store: markProcessed returns true only on first insertion. */
    static final class InMemoryStore implements InboxStore {
        private final Set<String> seen = new HashSet<>();

        @Override
        public boolean markProcessed(String messageId, String handler) {
            return seen.add(messageId + "|" + handler);
        }
    }

    @Test
    void firstSeenRunsHandlerDuplicateSkips() {
        DefaultInboxService inbox = new DefaultInboxService(new InMemoryStore());
        InboxBehavior behavior = new InboxBehavior(inbox);
        AtomicInteger invocations = new AtomicInteger();

        GuardedCommand command = new GuardedCommand("msg-1");

        String first = behavior.handle(command, () -> {
            invocations.incrementAndGet();
            return "handled";
        });
        assertEquals("handled", first);
        assertEquals(1, invocations.get());

        String second = behavior.handle(command, () -> {
            invocations.incrementAndGet();
            return "handled";
        });
        assertNull(second);
        assertEquals(1, invocations.get());
    }

    @Test
    void supportsOnlyIdempotentRequests() {
        InboxBehavior behavior = new InboxBehavior(new DefaultInboxService(new InMemoryStore()));
        assertEquals(true, behavior.supports(new GuardedCommand("k")));
        assertEquals(false, behavior.supports(new PlainCommand("v")));
    }

    @Test
    void defaultInboxServiceDelegatesToStore() {
        DefaultInboxService inbox = new DefaultInboxService(new InMemoryStore());
        assertEquals(true, inbox.firstSeen("m", "h"));
        assertEquals(false, inbox.firstSeen("m", "h"));
    }
}
