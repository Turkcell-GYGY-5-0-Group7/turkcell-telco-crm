# starter-inbox

Inbox-based idempotency for Telco CRM consumers (ADR-005).

## What it provides

- `InboxService` bean (`DefaultInboxService`) and `JdbcInboxStore` writing to `inbox_message`.
- `InboxBehavior` registered as a mediator `PipelineBehavior`, so any request implementing
  `IdempotentRequest` is processed at most once per handler.

## Usage

Add the dependency (version managed by `platform-bom`):

```xml
<dependency>
  <groupId>com.telco.platform</groupId>
  <artifactId>starter-inbox</artifactId>
</dependency>
```

Include the platform Flyway migrations so the `inbox_message` table is created:

```properties
spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform
```

Make a command idempotent by implementing `IdempotentRequest`:

```java
public record ProcessPaymentCommand(String paymentRequestId, ...) implements Command<Unit>, IdempotentRequest {
    @Override public String idempotencyKey() { return paymentRequestId; }
}
```

## Configuration (`telco.platform.inbox.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Enables the inbox auto-configuration. |
| `table` | `inbox_message` | Inbox table name. |
