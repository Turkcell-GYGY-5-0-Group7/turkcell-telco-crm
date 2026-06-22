package com.telco.reference.application;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import com.telco.reference.domain.DemoItem;
import com.telco.reference.infrastructure.DemoItemRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a {@link DemoItem} and publishes {@code demoitem.created.v1} through the transactional
 * outbox. The mediator TransactionBehavior wraps this command in a transaction, so the JPA insert
 * and the outbox row commit atomically; Debezium then delivers the event (ADR-005, ADR-009).
 */
@Component
public class CreateDemoItemCommandHandler implements CommandHandler<CreateDemoItemCommand, DemoItemResponse> {

    private static final String AGGREGATE_TYPE = "DemoItem";
    private static final String EVENT_TYPE = "demoitem.created.v1";

    private final DemoItemRepository repository;
    private final OutboxService outbox;

    public CreateDemoItemCommandHandler(DemoItemRepository repository, OutboxService outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Override
    public DemoItemResponse handle(CreateDemoItemCommand command) {
        DemoItem item = new DemoItem(UUID.randomUUID(), command.name(), Instant.now());
        repository.save(item);
        outbox.publish(AGGREGATE_TYPE, item.getId().toString(), EVENT_TYPE,
                new DemoItemCreatedV1(item.getId().toString(), item.getName(), item.getCreatedAt()));
        return DemoItemResponse.from(item);
    }
}
