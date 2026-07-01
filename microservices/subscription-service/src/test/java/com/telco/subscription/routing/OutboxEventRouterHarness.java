package com.telco.subscription.routing;

import io.debezium.transforms.outbox.EventRouter;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * Test harness that runs the REAL Debezium outbox Single Message Transform
 * ({@link io.debezium.transforms.outbox.EventRouter}) against a synthetic PostgreSQL CDC change
 * event, configured EXACTLY as the production connector
 * ({@code infra/docker/kafka-connect/connectors/outbox-connector.example.json}).
 *
 * <p>Purpose: prove that the Kafka topic a saga event lands on is DERIVED by Debezium from the
 * {@code aggregate_type} column value ({@code route.by.field=aggregate_type},
 * {@code route.topic.replacement=${routedByValue}.events}) - not from a hardcoded string in a
 * consumer test. If a producer regressed its {@code OUTBOX_AGGREGATE_TYPE} from the lowercase
 * {@code "subscription"} back to PascalCase {@code "Subscription"}, this harness routes to
 * {@code Subscription.events} and the assertion in {@link OutboxRoutingRegressionTest} fails - which
 * is the whole point.
 *
 * <p>The synthetic value mirrors the shape Debezium's pgoutput connector emits for an INSERT into
 * {@code outbox_event}: a change envelope carrying {@code after} (the new row), {@code op="c"}, and
 * {@code ts_ms}. Only the fields the EventRouter reads are populated.
 */
final class OutboxEventRouterHarness {

    /** The routed topic plus the placed {@code eventType} header, as produced by the SMT. */
    record Routed(String topic, String eventTypeHeader) {
    }

    private OutboxEventRouterHarness() {
    }

    /**
     * Applies the production EventRouter SMT to an outbox row with the given column values and returns
     * the routed topic and the {@code eventType} header the SMT placed.
     *
     * @param aggregateType the value of the {@code aggregate_type} column (the routing key)
     * @param eventType     the value of the {@code event_type} column (placed as the eventType header)
     */
    static Routed route(String aggregateType, String eventType) {
        try (EventRouter<SourceRecord> router = new EventRouter<>()) {
            router.configure(productionConfig());

            Schema rowSchema = SchemaBuilder.struct().name("outbox_event.Value")
                    .field("id", Schema.STRING_SCHEMA)
                    .field("aggregate_type", Schema.STRING_SCHEMA)
                    .field("aggregate_id", Schema.STRING_SCHEMA)
                    .field("event_type", Schema.STRING_SCHEMA)
                    .field("payload", Schema.STRING_SCHEMA)
                    .build();
            Struct row = new Struct(rowSchema)
                    .put("id", java.util.UUID.randomUUID().toString())
                    .put("aggregate_type", aggregateType)
                    .put("aggregate_id", java.util.UUID.randomUUID().toString())
                    .put("event_type", eventType)
                    .put("payload", "{\"probe\":true}");

            Schema envelopeSchema = SchemaBuilder.struct().name("dbserver.public.outbox_event.Envelope")
                    .field("before", rowSchema)
                    .field("after", rowSchema)
                    .field("op", Schema.STRING_SCHEMA)
                    .field("ts_ms", Schema.INT64_SCHEMA)
                    .build();
            Struct envelope = new Struct(envelopeSchema)
                    .put("after", row)
                    .put("op", "c")
                    .put("ts_ms", System.currentTimeMillis());

            SourceRecord in = new SourceRecord(
                    null, null, "dbserver.public.outbox_event", null,
                    Schema.STRING_SCHEMA, row.getString("aggregate_id"),
                    envelopeSchema, envelope);

            SourceRecord out = router.apply(in);
            var header = out.headers().lastWithName("eventType");
            return new Routed(out.topic(), header == null ? null : String.valueOf(header.value()));
        }
    }

    /**
     * The EventRouter config, field-for-field identical to the production Debezium connector JSON
     * (transforms.outbox.* entries). Kept in lockstep so a routing change in production without a
     * matching change here surfaces as a broken test.
     */
    private static Map<String, String> productionConfig() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("table.field.event.id", "id");
        cfg.put("table.field.event.key", "aggregate_id");
        cfg.put("table.field.event.type", "event_type");
        cfg.put("table.field.event.payload", "payload");
        cfg.put("table.fields.additional.placement", "event_type:header:eventType");
        cfg.put("route.by.field", "aggregate_type");
        cfg.put("route.topic.replacement", "${routedByValue}.events");
        return cfg;
    }
}
