# starter-outbox

Transactional outbox write-side for Telco CRM services (ADR-005, ADR-009).

## What it provides

- `OutboxService` bean (`DefaultOutboxService`) to publish domain events from within a business
  transaction. The outbox row commits atomically with the business write.
- `JdbcOutboxStore` backed by the service `JdbcTemplate`, writing to the `outbox_event` table.
  Failed deliveries increment `retry_count` and record the reason in `error_message`; a successful
  publish clears `error_message`.
- `JacksonEventSerializer` using the application `ObjectMapper`.
- An optional polling relay, disabled by default (Debezium CDC is the primary delivery mechanism).

## Usage

Add the dependency (version managed by `platform-bom`):

```xml
<dependency>
  <groupId>com.telco.platform</groupId>
  <artifactId>starter-outbox</artifactId>
</dependency>
```

Include the platform Flyway migrations so the `outbox_event` table is created:

```properties
spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform
```

Publish an event inside a command handler (which runs in a transaction via starter-mediator):

```java
outboxService.publish("Customer", customerId, "customer.registered.v1", event);
```

## Configuration (`telco.platform.outbox.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Enables the outbox auto-configuration. |
| `table` | `outbox_event` | Outbox table name. |
| `relay.enabled` | `false` | Enables the optional polling relay (use Debezium instead in production). |
| `relay.batch-size` | `100` | Max rows per relay poll. |
| `relay.poll-interval-ms` | `5000` | Relay poll interval. |
