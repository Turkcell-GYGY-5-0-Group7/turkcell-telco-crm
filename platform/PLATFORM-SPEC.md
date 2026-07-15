# Platform Implementation Spec (Contract for Implementers)

This document is the authoritative contract for implementing the Telco CRM platform modules.
It encodes the decisions in ADR-004, ADR-005, ADR-007, ADR-008, ADR-009, ADR-011, ADR-012,
ADR-015, ADR-016, ADR-018, ADR-019, ADR-020 and `CLAUDE.md`. Where this spec and an ADR
disagree, the ADR wins; raise the conflict rather than guessing.

Hard rules:

- No emojis anywhere (code, comments, docs).
- `platform-core/*` modules MUST NOT import Spring. Allowed: JDK, slf4j-api, jakarta.validation-api
  (optional), jakarta annotations, Jackson annotations (`com.fasterxml.jackson.annotation.*` only).
- Spring lives ONLY in `platform-autoconfigure` and `platform-starters/*`.
- Microservices will depend ONLY on starters (ADR-018). Do not design APIs assuming otherwise.
- Java 21. Use records for immutable value types; sealed types where it adds clarity.
- Every public type gets a short Javadoc. Keep business logic out of platform modules.

Reference (adapt, do not copy verbatim; it is Spring-coupled and uses a different ApiResult):
`../../spring-microservices/microservices/common/`.

---

## 1. Coordinates and Packages

GroupId `com.telco.platform`, version `1.0.0-SNAPSHOT`. Base package per module:

| Module | artifactId | Base package |
| --- | --- | --- |
| common | platform-common | `com.telco.platform.common` |
| cqrs | platform-cqrs | `com.telco.platform.cqrs` |
| mediator | platform-mediator | `com.telco.platform.mediator` |
| outbox | platform-outbox | `com.telco.platform.outbox` |
| inbox | platform-inbox | `com.telco.platform.inbox` |
| lock | platform-lock | `com.telco.platform.lock` |
| event-contracts | platform-event-contracts | `com.telco.platform.events` |
| autoconfigure | platform-autoconfigure | `com.telco.platform.autoconfigure` |
| starter-api | starter-api | `com.telco.platform.starter.api` |
| starter-mediator | starter-mediator | `com.telco.platform.starter.mediator` |
| starter-security | starter-security | `com.telco.platform.starter.security` |
| starter-outbox | starter-outbox | `com.telco.platform.starter.outbox` |
| starter-inbox | starter-inbox | `com.telco.platform.starter.inbox` |
| starter-observability | starter-observability | `com.telco.platform.starter.observability` |
| starter-lock | starter-lock | `com.telco.platform.starter.lock` |

Sub-packages: `.api`, `.exception`, `.context`, `.util` (common); `.pipeline`, `.behavior`,
`.behavior.support` (mediator). All POMs already exist; implementers add Java/resources and may
add a dependency within their own module if strictly required (do not change artifactIds/parents).

---

## 2. platform-common

### 2.1 api package (ADR-015 response contract)

`ApiResult<T>` (record) is the universal external response wrapper:

```java
public record ApiResult<T>(boolean success, T data, ApiError error, ApiMeta meta) {
  public static <T> ApiResult<T> ok(T data, ApiMeta meta);
  public static <T> ApiResult<T> failure(ApiError error, ApiMeta meta);
}
```

`ApiError` (record): `String code, String message, Map<String,Object> details, String traceId`.
`ApiMeta` (record): `String traceId, String correlationId, Instant timestamp, String service, String path`.
Annotate with `@JsonInclude(JsonInclude.Include.NON_NULL)`. `details` may be null/empty.

`PageResult<T>` (offset, record): `List<T> content, int page, int size, long totalElements, int totalPages`.
`CursorPage<T>` (record): `List<T> content, String nextCursor, boolean hasNext, int limit`.

### 2.2 exception package

Sealed base `PlatformException extends RuntimeException` carrying an `ErrorCode code` and an
optional `Map<String,Object> details`. Permitted subtypes (each maps to an HTTP status; the
mapping itself lives in starter-api, not here):

- `ResourceNotFoundException` (404)
- `ValidationException` (400) - carries field violations in `details`
- `ConflictException` (409)
- `UnauthenticatedException` (401)
- `AccessDeniedException` (403)
- `BusinessRuleException` (422)
- `DependencyFailureException` (502/503) - downstream failure

`ErrorCode` is an interface: `String code()`. Provide an enum `CommonErrorCode implements ErrorCode`
with at least: `RESOURCE_NOT_FOUND, VALIDATION_FAILED, CONFLICT, UNAUTHENTICATED, ACCESS_DENIED,
BUSINESS_RULE_VIOLATION, DEPENDENCY_FAILURE, INTERNAL_ERROR`.

### 2.3 context package (no Spring; pure ThreadLocal)

`UserContext` (record): `String userId, Set<String> roles, String tenantId`. Helper
`boolean hasRole(String)`. Provide `UserContext.anonymous()`.

`UserContextHolder`: ThreadLocal store - `set(UserContext)`, `Optional<UserContext> get()`, `clear()`.

`CorrelationContext` (record): `String traceId, String correlationId`. `CorrelationContextHolder`:
ThreadLocal - `set/get/clear`.

`CurrentUserProvider` (interface): `UserContext currentUser()` - default impl returns
`UserContext.anonymous()` (overridden by starter-security).

`CorrelationConstants`: `HEADER_CORRELATION_ID="X-Correlation-Id"`, `MDC_TRACE_ID="traceId"`,
`MDC_CORRELATION_ID="correlationId"`, header names `X-User-Id="X-User-Id"`, `X-User-Roles="X-User-Roles"`.

---

## 3. platform-cqrs

Pure marker/handler contracts. No logic.

```java
public interface Request<R> {}                 // common super-type (internal use)
public interface Command<R> extends Request<R> {}
public interface Query<R>   extends Request<R> {}
public interface Event {}                       // immutable; implementations are records

public interface CommandHandler<C extends Command<R>, R> { R handle(C command); }
public interface QueryHandler<Q extends Query<R>, R>     { R handle(Q query); }
public interface EventHandler<E extends Event>           { void handle(E event); }

public final class Unit { public static final Unit INSTANCE = new Unit(); private Unit(){} }
```

Use `Command<Unit>` for void commands. Do not add Spring or annotations here.

---

## 4. platform-mediator

### 4.1 pipeline

```java
public interface RequestHandlerDelegate<R> { R invoke(); }       // functional

public interface PipelineBehavior {
  boolean supports(Object request);
  <R> R handle(Object request, RequestHandlerDelegate<R> next);
  default int order() { return PipelineOrder.DEFAULT; }
}

public final class PipelineOrder {                  // lower = outer (runs first)
  public static final int VALIDATION   = 100;
  public static final int AUTHORIZATION = 200;
  public static final int LOGGING      = 300;
  public static final int INBOX        = 350;
  public static final int TRANSACTION  = 400;
  public static final int PERFORMANCE  = 500;       // innermost, closest to handler
  public static final int DEFAULT      = 1000;
}
```

### 4.2 dispatcher (pure)

```java
public interface Mediator {
  <R> R send(Command<R> command);
  <R> R query(Query<R> query);
  void publish(Event event);
}

public interface HandlerRegistry {                  // implemented by starter-mediator (Spring)
  <R> CommandHandler<Command<R>, R> commandHandler(Class<?> commandType);
  <R> QueryHandler<Query<R>, R> queryHandler(Class<?> queryType);
  List<EventHandler<Event>> eventHandlers(Class<?> eventType);
}

public final class InProcessMediator implements Mediator { /* registry + sorted behaviors */ }
```

`InProcessMediator` sorts behaviors ascending by `order()`, builds the chain so index 0 is the
outermost wrapper, applies only behaviors whose `supports(request)` is true, then dispatches to the
handler from the registry. Missing command/query handler -> `IllegalStateException`. Events with no
handlers are a no-op. No Spring (sort with `Comparator.comparingInt(PipelineBehavior::order)`).

### 4.3 behaviors (pure; depend on ports, not Spring)

- `ValidationBehavior` - constructed with a `jakarta.validation.Validator`; validates the request,
  throws `ValidationException` (from common) with violations in `details`. order=VALIDATION.
- `AuthorizationBehavior(CurrentUserProvider, List<AuthorizationRule>)` - for each rule that
  `supports(request)`, call `rule.check(request, user)`; throw `AccessDeniedException`/
  `UnauthenticatedException` as appropriate. order=AUTHORIZATION.
- `LoggingBehavior(String serviceName, List<RequestLogWriter> writers)` - logs start/end via slf4j
  and forwards a `RequestLogEntry` to each writer; skips requests implementing `NotLoggable`. order=LOGGING.
- `PerformanceBehavior(long slowThresholdMs)` - times the inner call, slf4j-warns when slow. order=PERFORMANCE.
- `TransactionBehavior(TransactionRunner runner)` - wraps commands (not queries) in
  `runner.executeInTransaction(...)`. `supports` returns true only for `Command`. order=TRANSACTION.

Ports in `behavior.support`:

```java
public interface TransactionRunner { <R> R executeInTransaction(Supplier<R> action); }
public interface AuthorizationRule { boolean supports(Object request); void check(Object request, UserContext user); }
public interface RequestLogWriter { void write(RequestLogEntry entry); }
public record RequestLogEntry(String service, String requestType, String requestKind,
                              String userId, String correlationId, long durationMs,
                              boolean success, String errorCode, Instant timestamp) {}
public interface NotLoggable {}   // marker; requests implementing it are not logged
```

Provide `Slf4jRequestLogWriter` (default, in core) using slf4j only. DB-backed log persistence is
out of scope for this pass.

---

## 5. platform-outbox (write-side; Debezium delivers, ADR-005/009)

```java
public enum OutboxStatus { NEW, PUBLISHED, FAILED }

public record OutboxRecord(UUID id, String aggregateType, String aggregateId, String eventType,
                           String payload, String headers, String traceId, String correlationId,
                           Instant createdAt, OutboxStatus status) {}

public interface EventSerializer { String serialize(Object payload); }   // Jackson impl in starter

public interface OutboxStore {                 // JDBC impl in starter-outbox
  void append(OutboxRecord record);
  List<OutboxRecord> findByStatus(OutboxStatus status, int limit);   // for optional relay fallback
  void markPublished(UUID id);
  void markFailed(UUID id);
}

public interface OutboxService {
  void publish(String aggregateType, String aggregateId, String eventType, Object payload);
}

public final class DefaultOutboxService implements OutboxService { /* serialize + capture
  correlation/trace from CorrelationContextHolder + append within the caller's transaction */ }
```

`eventType` MUST follow `domain.event.v1`. The append happens in the caller's transaction so the
DB write and the outbox row commit atomically; Debezium captures the insert. Provide an OPTIONAL
polling relay class in starter-outbox, disabled by default.

`aggregateType` is the **Debezium routing key**, not the DDD aggregate class name. Debezium's
EventRouter routes on the `aggregate_type` column via `${routedByValue}.events`, so `aggregateType`
MUST be the **lowercase event domain** (e.g. `subscription`, `order`, `payment`) and MUST match the
lowercase `domain.events` topic convention consumers subscribe to (event-catalog). It MUST NOT be
PascalCase - a PascalCase value routes to the wrong topic (`Subscription.events`) and no consumer
receives it. When a handler also writes an audit row, use a SEPARATE PascalCase constant for the
audit entity-type (ADR-021) and a distinct lowercase constant for `publish(...)`; do not share one
symbol across both sinks. (`DefaultOutboxService` MAY fail-fast on a non-lowercase `aggregateType`.)

---

## 6. platform-inbox (idempotent consume, ADR-005)

```java
public interface IdempotentRequest { String idempotencyKey(); }   // marker for inbox-guarded requests

public interface InboxStore {                    // JDBC impl in starter-inbox
  boolean markProcessed(String messageId, String handler);   // true if newly inserted (not a duplicate)
}

public interface InboxService { boolean firstSeen(String messageId, String handler); }
public final class DefaultInboxService implements InboxService { /* delegates to InboxStore */ }

public final class InboxBehavior implements PipelineBehavior {  // order=PipelineOrder.INBOX
  // supports(request) -> request instanceof IdempotentRequest
  // if not firstSeen -> skip handler (return null), else proceed and the store row marks it done
}
```

---

## 7. platform-lock (distributed locking, ADR-024)

```java
public interface DistributedLock {
  LockHandle acquire(String key, Duration leaseTime);
  <T> T withLock(String key, Duration leaseTime, Callable<T> action);
  void withLock(String key, Duration leaseTime, Runnable action);
}

public interface LockHandle extends AutoCloseable {   // Redisson RLock wrapper in starter-lock
  String key();
  void release();     // idempotent
  default void close() { release(); }
}

public enum LockErrorCode implements ErrorCode { LOCK_ACQUISITION_FAILED }   // no new exception type
```

Cross-instance mutual exclusion (multiple pods of one service, or two services) that a single-JVM
lock or a Postgres `SELECT ... FOR UPDATE` cannot provide. `leaseTime == null` requests a
watchdog-managed lease (the Redisson implementation auto-renews it while the holder is alive - the
right choice for variable-duration work); a non-null `Duration` requests an explicit lease that
hard-expires on schedule regardless of holder liveness (the right choice for a bounded critical
section). Callers choose per call site; the platform does not force one mode (ADR-024 Section 4).

Fails CLOSED: on connection failure, timeout, or the wait-time budget expiring without acquiring the
lock, the implementation throws the platform's EXISTING `DependencyFailureException` (already
503-mapped by `GlobalExceptionHandler`) constructed with `LockErrorCode.LOCK_ACQUISITION_FAILED` - it
does NOT define a dedicated exception type. `PlatformException` (Section 2.2) is `sealed` with a
`permits` list scoped to its own package (`com.telco.platform.common.exception`), and this codebase
has no `module-info.java`, so a type in `com.telco.platform.lock` cannot join that hierarchy (ADR-024
Section 5). This is the one significant departure from the outbox/inbox port shape above: do not
add a `LockAcquisitionException` here even though the naming convention elsewhere might suggest it.

Spring-free, matching this module's purity constraint: no Redisson or Spring types appear anywhere
in `platform-lock`'s public API or dependency graph. The Redisson-backed implementation
(`RedissonDistributedLock`, `RedissonLockHandle`) lives entirely in `starter-lock` (Section 10.7).

---

## 8. platform-event-contracts (Avro, ADR-019)

Avro schemas under `src/main/avro/*.avsc`, namespace `com.telco.platform.events.<domain>`.
Generated into `target/generated-sources/avro` by the configured avro-maven-plugin.

Define a shared `EventEnvelope.avsc` (fields: eventId string, eventType string, occurredAt
long timestamp-millis, traceId string, correlationId string, payload bytes or union) AND at least
these MVP schemas matching `docs/architecture/event-catalog.md`:
`customer-registered.avsc`, `order-created.avsc`, `payment-completed.avsc`,
`subscription-activated.avsc`, `invoice-generated.avsc`. Versioned record names end in `V1`
(e.g. `CustomerRegisteredV1`). Keep fields nullable-friendly for backward compatibility.

---

## 9. platform-autoconfigure (shared Spring primitives)

- `PlatformProperties` constants: property prefix root `telco.platform`.
- `PlatformJacksonAutoConfiguration` - a `Jackson2ObjectMapperBuilderCustomizer` registering
  JavaTimeModule, `WRITE_DATES_AS_TIMESTAMPS=false`, `NON_NULL`.
- Shared `@ConditionalOn...` helpers if needed.
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

Keep this module small. Capability-specific autoconfig lives in the owning starter.

---

## 10. Starters (Spring wiring; ADR-018)

Each starter provides `@AutoConfiguration` classes, conditional beans
(`@ConditionalOnMissingBean`, `@ConditionalOnClass`, `@ConditionalOnProperty`), typed
`@ConfigurationProperties`, and a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
file. No business logic. Property prefix root: `telco.platform`.

### 10.1 starter-api
`@RestControllerAdvice` `GlobalExceptionHandler` mapping `PlatformException` subtypes to HTTP
status + `ApiResult.failure(...)`, populating `ApiMeta` from `CorrelationContextHolder` and the
request path; maps `MethodArgumentNotValidException`/`ConstraintViolationException` to 400; falls
back to 500 `INTERNAL_ERROR` without leaking stack traces. Conditional on web. Property:
`telco.platform.api.enabled` (default true).

### 10.2 starter-mediator
`MediatorAutoConfiguration` provides: Spring `HandlerRegistry` (resolves CommandHandler/
QueryHandler/EventHandler beans), the `Mediator` bean (`InProcessMediator`), and behavior beans -
Performance, Logging, Authorization always-on; Validation `@ConditionalOnBean(Validator.class)`;
Transaction `@ConditionalOnBean(PlatformTransactionManager.class)` with a `SpringTransactionRunner`
adapter. Order config AFTER Hibernate/DataSource/Transaction/Validation auto-configurations (use
`afterName`, as in the reference). Provide `CurrentUserProvider` default bean
`@ConditionalOnMissingBean`. Props under `telco.platform.mediator.*` (e.g.
`performance.slow-threshold-ms` default 500).

### 10.3 starter-security
`JwtProperties` (`telco.platform.security.jwt.*`: secret or public-key, issuer, expiry, header
names). `JwtService` (validate + parse claims; optional issue helper). `JwtAuthFilter`
(`OncePerRequestFilter`): if `telco.platform.security.gateway-trust.enabled`, trust `X-User-Id`/
`X-User-Roles`; otherwise validate the Bearer JWT. Populate `UserContextHolder` and Spring
`SecurityContext`. Provide a `CurrentUserProvider` bean reading the user context
(overrides the mediator default). `SecurityAutoConfiguration` conditional on
`telco.platform.security.enabled` and on security/web classes.

### 10.4 starter-outbox
`JdbcOutboxStore` (spring-jdbc), `JacksonEventSerializer`, `OutboxAutoConfiguration` wiring
`DefaultOutboxService`. Flyway migration creating a Debezium-compatible outbox table. Optional
disabled relay scheduler (`telco.platform.outbox.relay.enabled=false`). Props
`telco.platform.outbox.*`.

### 10.5 starter-inbox
`JdbcInboxStore` (spring-jdbc), `InboxAutoConfiguration` wiring `DefaultInboxService` and
contributing `InboxBehavior` as a `PipelineBehavior` bean. Flyway migration for the inbox table.
Props `telco.platform.inbox.*`.

### 10.6 starter-observability
`CorrelationFilter` (`OncePerRequestFilter`): read or generate `X-Correlation-Id`, set
`CorrelationContextHolder` + MDC (`traceId`, `correlationId`), echo the header on the response,
clear on completion. `ObservabilityAutoConfiguration` registers the filter (conditional on web)
and a micrometer tracing customizer (conditional on micrometer classes). Props
`telco.platform.observability.*` (e.g. `correlation.enabled` default true).

### 10.7 starter-lock (ADR-024)
`RedissonDistributedLock implements DistributedLock` (built on `RedissonClient.getLock(key)`/`RLock`;
`leaseTime == null` -> watchdog-managed, a `Duration` -> explicit hard-expiry; on failure throws
`DependencyFailureException` with `LockErrorCode.LOCK_ACQUISITION_FAILED`, per Section 7 - no
`GlobalExceptionHandler` change needed, since that mapping already exists). `RedissonLockHandle`
(wraps `RLock`; `release()` guards with `isHeldByCurrentThread()` rather than catching
`IllegalMonitorStateException`). `LockAutoConfiguration`: builds the `RedissonClient` bean from
`telco.platform.lock.redis.address`, falling back to `spring.data.redis.host`/`port` (NOT
`spring.data.redis.password` - set a full `redis://:<password>@host:port` address explicitly if the
target Redis requires auth); conditional on `telco.platform.lock.enabled` (default true) and
Redisson on the classpath; `@ConditionalOnMissingBean` override points for both the `RedissonClient`
and the `DistributedLock` beans. Depends on the plain `org.redisson:redisson` client artifact, not
Redisson's own `redisson-spring-boot-starter` - the platform, not a third-party starter, owns the
`AutoConfiguration` and property surface. No Flyway migration (Redis-backed, not a table). Props
under `telco.platform.lock.*`:

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master on/off switch. |
| `redis.address` | falls back to `spring.data.redis.host`/`port`, else `redis://localhost:6379` | Redisson single-server connection target. |
| `wait-time` | `5s` | Max time a caller blocks trying to acquire before failing closed. |
| `watchdog-timeout` | `30s` | Redisson's internal lock-watchdog-timeout, used for watchdog-managed leases (`leaseTime == null`). |

Strictly optional per ADR-018: a service adds `starter-lock` only if it needs cross-instance
coordination; no existing starter or service gained a transitive dependency on it. First real
consumers: `subscription-service`'s MSISDN reservation-expiry reaper (explicit lease) and
`billing-service`'s bill-run (watchdog-managed lease) - Sprint 17 Features 17.3/17.4.

---

## 11. Database Migrations (Flyway, ADR-016)

Platform tables ship as Flyway scripts on the classpath under `db/migration/platform/` in the
owning starter, versioned at `V900+` to avoid collision with service migrations. Services add
`spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform`. Use
`CREATE TABLE IF NOT EXISTS`. Document this in each starter's README.

Outbox table (PostgreSQL, Debezium outbox-connector friendly):
`id uuid primary key, aggregate_type varchar, aggregate_id varchar, event_type varchar,
payload jsonb, headers jsonb, trace_id varchar, correlation_id varchar,
created_at timestamptz not null default now(), status varchar not null default 'NEW'`.
Index on `(status, created_at)`.

Inbox table:
`message_id varchar not null, handler varchar not null, processed_at timestamptz not null default now(),
primary key (message_id, handler)`.

---

## 12. Build and Verification

From `telco-crm/platform`:

- Core only: `mvn -q -DskipTests install -pl platform-bom,platform-core,platform-core/common,platform-core/cqrs,platform-core/mediator,platform-core/outbox,platform-core/inbox`
- Full reactor: `mvn -q -DskipTests install` (after all modules have sources)
- With tests: `mvn -q install`

Each module MUST compile and (where applicable) pass its unit tests. Add focused unit tests for:
mediator ordering/short-circuit, outbox record building, inbox first-seen logic, ApiResult/exception
mapping. Do not commit; the parent task integrates and builds.
