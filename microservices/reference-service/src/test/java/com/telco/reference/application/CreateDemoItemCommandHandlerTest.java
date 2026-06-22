package com.telco.reference.application;

import com.telco.platform.outbox.OutboxService;
import com.telco.reference.domain.DemoItem;
import com.telco.reference.infrastructure.DemoItemRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test (no Spring/DB) per ADR-013. Verifies the handler persists the item and publishes the
 * creation event to the outbox with the correct type.
 */
class CreateDemoItemCommandHandlerTest {

    @Test
    void persistsItemAndPublishesEvent() {
        DemoItemRepository repository = mock(DemoItemRepository.class);
        when(repository.save(any(DemoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> publishedEventTypes = new ArrayList<>();
        OutboxService outbox = (aggregateType, aggregateId, eventType, payload) ->
                publishedEventTypes.add(eventType);

        var handler = new CreateDemoItemCommandHandler(repository, outbox);
        DemoItemResponse response = handler.handle(new CreateDemoItemCommand("widget"));

        assertNotNull(response.id());
        assertEquals("widget", response.name());
        assertEquals(List.of("demoitem.created.v1"), publishedEventTypes);
    }
}
