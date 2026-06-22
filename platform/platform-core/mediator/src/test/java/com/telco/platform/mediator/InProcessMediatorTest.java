package com.telco.platform.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.EventHandler;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InProcessMediatorTest {

    private PipelineBehavior recordingBehavior(String tag, int order, List<String> trace) {
        return new PipelineBehavior() {
            @Override
            public boolean supports(Object request) {
                return true;
            }

            @Override
            public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
                trace.add("before:" + tag);
                R result = next.invoke();
                trace.add("after:" + tag);
                return result;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    @Test
    void appliesBehaviorsInAscendingOrderWithLowestOutermost() {
        List<String> trace = new ArrayList<>();
        RecordingRegistry registry = new RecordingRegistry();
        registry.registerCommand(TestRequests.SampleCommand.class,
                (CommandHandler<TestRequests.SampleCommand, String>) c -> {
                    trace.add("handler");
                    return c.value();
                });

        // Provide out of natural order to prove sorting.
        List<PipelineBehavior> behaviors = List.of(
                recordingBehavior("high", 500, trace),
                recordingBehavior("low", 100, trace),
                recordingBehavior("mid", 300, trace));

        InProcessMediator mediator = new InProcessMediator(registry, behaviors);
        String result = mediator.send(new TestRequests.SampleCommand("ok"));

        assertEquals("ok", result);
        assertEquals(List.of(
                "before:low", "before:mid", "before:high",
                "handler",
                "after:high", "after:mid", "after:low"), trace);
    }

    @Test
    void skipsBehaviorsWhoseSupportsIsFalse() {
        AtomicInteger applied = new AtomicInteger();
        RecordingRegistry registry = new RecordingRegistry();
        registry.registerQuery(TestRequests.SampleQuery.class,
                (QueryHandler<TestRequests.SampleQuery, String>) q -> q.value());

        PipelineBehavior nonMatching = new PipelineBehavior() {
            @Override
            public boolean supports(Object request) {
                return false;
            }

            @Override
            public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
                applied.incrementAndGet();
                return next.invoke();
            }
        };

        InProcessMediator mediator = new InProcessMediator(registry, List.of(nonMatching));
        String result = mediator.query(new TestRequests.SampleQuery("v"));

        assertEquals("v", result);
        assertEquals(0, applied.get());
    }

    @Test
    void missingCommandHandlerThrowsIllegalState() {
        InProcessMediator mediator = new InProcessMediator(new RecordingRegistry(), List.of());
        assertThrows(IllegalStateException.class,
                () -> mediator.send(new TestRequests.SampleCommand("x")));
    }

    @Test
    void missingQueryHandlerThrowsIllegalState() {
        InProcessMediator mediator = new InProcessMediator(new RecordingRegistry(), List.of());
        assertThrows(IllegalStateException.class,
                () -> mediator.query(new TestRequests.SampleQuery("x")));
    }

    @Test
    void publishWithoutHandlersIsNoOp() {
        InProcessMediator mediator = new InProcessMediator(new RecordingRegistry(), List.of());
        mediator.publish(new TestRequests.SampleEvent("e"));
    }

    @Test
    void publishInvokesAllRegisteredEventHandlers() {
        List<String> seen = new ArrayList<>();
        RecordingRegistry registry = new RecordingRegistry();
        EventHandler<Event> h1 = e -> seen.add("h1");
        EventHandler<Event> h2 = e -> seen.add("h2");
        registry.registerEvents(TestRequests.SampleEvent.class, List.of(h1, h2));

        InProcessMediator mediator = new InProcessMediator(registry, List.of());
        mediator.publish(new TestRequests.SampleEvent("e"));

        assertEquals(List.of("h1", "h2"), seen);
    }

    @Test
    void commandDispatchReturnsHandlerResult() {
        RecordingRegistry registry = new RecordingRegistry();
        registry.registerCommand(TestRequests.SampleCommand.class,
                (CommandHandler<TestRequests.SampleCommand, String>) c -> "handled:" + c.value());
        InProcessMediator mediator = new InProcessMediator(registry, List.of());
        Command<String> cmd = new TestRequests.SampleCommand("a");
        assertEquals("handled:a", mediator.send(cmd));
    }
}
