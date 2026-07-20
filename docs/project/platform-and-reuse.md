# Platform & Reuse-Before-Build

**Rule:** if a capability is listed here, do not re-implement it. This page condenses the full
[Platform Capability Catalog](../architecture/platform-capabilities.md) - read that page before
writing any cross-cutting infrastructure. The canonical worked example is
`microservices/reference-service`; copy its shape when scaffolding something new.

Services depend only on platform **starters**, never on `platform-core` directly
([ADR-018](../adr/ADR-018-platform-starter-dependency-model.md)). Everything below arrives
transitively through the starter.

## Available now

| Starter | Capability | Import | Use for |
| --- | --- | --- | --- |
| `starter-api` | `ApiResult<T>`, `ApiError`, `ApiMeta` | `com.telco.platform.common.api.*` | Every external response wrapper (mandatory, [ADR-015](../adr/ADR-015-api-design-standards.md)) |
| `starter-api` | `PageResult<T>`, `CursorPage<T>` | `com.telco.platform.common.api.*` | Offset and cursor pagination |
| `starter-api` | `GlobalExceptionHandler` | auto | Maps exceptions to `ApiResult.failure` + HTTP status - never write your own |
| `starter-api` / `platform-common` | `PlatformException` subtypes | `com.telco.platform.common.exception.*` | `ResourceNotFoundException`, `ValidationException`, `ConflictException`, `BusinessRuleException`, `AccessDeniedException`, `UnauthenticatedException`, `DependencyFailureException` - each maps to the correct HTTP status |
| `platform-common` / `starter-security` / `starter-observability` | `UserContext`, `CorrelationContext` | `com.telco.platform.common.context.*` | Current user id/roles/tenant, traceId/correlationId |
| `starter-mediator` | `Command<R>`, `Query<R>`, `Event`, `Mediator` | `com.telco.platform.cqrs.*`, `com.telco.platform.mediator.Mediator` | Controllers call `Mediator.send`/`.query`/`.publish` - never a service class directly |
| `starter-mediator` | Pipeline behaviors | auto | Validation, Authorization, Logging, Transaction, Performance, Inbox - applied automatically |
| `starter-outbox` | `OutboxService` | `com.telco.platform.outbox.OutboxService` | `publish(aggregateType, aggregateId, eventType, payload)`, atomic with your DB write - never call Kafka directly |
| `starter-inbox` | `InboxService`, `IdempotentRequest` | `com.telco.platform.inbox.*` | Idempotent event consumption |
| `starter-lock` | `DistributedLock`, `LockHandle` | `com.telco.platform.lock.*` | Cross-instance mutual exclusion a single-JVM lock or `SELECT ... FOR UPDATE` cannot provide. Fails **closed**: on acquisition failure the guarded action never runs ([ADR-024](../adr/ADR-024-distributed-lock-strategy.md)) |
| `starter-security` | JWT validation + gateway trust | auto | Validates the Keycloak JWT or trusts gateway-forwarded identity headers - services never issue tokens ([ADR-011](../adr/ADR-011-security-foundation.md)) |
| `starter-observability` | `CorrelationFilter`, `@Sensitive`, `PiiMasker` | `com.telco.platform.common.masking.*` | traceId/correlationId on every request + structured logs; PII masking in logs/telemetry ([ADR-021](../adr/ADR-021-pii-and-data-masking-strategy.md)) |
| build | `platform-bom` | - | Inherit **all** dependency versions - never hardcode one in a service pom |

## New-service checklist

1. Copy `microservices/service-template` (or `reference-service` for the fuller JPA/outbox
   shape) - see [ADR-017](../adr/ADR-017-service-template-standard.md).
2. Add the mandatory starters: `starter-api`, `starter-security`, `starter-observability`. Add
   `starter-mediator`, `starter-outbox`, `starter-inbox` as the domain needs them.
3. Declare, in the service's own `README.md`, its **Architecture Mode**
   ([ADR-004](../adr/ADR-004-architecture-style.md)) and **Infrastructure Profile**
   ([ADR-006](../adr/ADR-006-database-strategy.md)) - see the [Service Catalog](../architecture/service-catalog.md).
4. Reuse everything in the table above. Do not re-create `ApiResult`, error types, context,
   pagination, correlation, or masking.
5. Point Flyway at `classpath:db/migration,classpath:db/migration/platform` so the shared
   outbox/inbox tables apply.
6. Add Testcontainers integration tests ([ADR-013](../adr/ADR-013-testing-strategy.md)).

## Planned - not yet available

These are identified gaps. A service may implement one locally if it genuinely needs it now, but
flag it so it migrates to the platform instead of diverging further:

| Planned capability | Likely home |
| --- | --- |
| `BaseEntity` + JPA auditing + soft-delete | `starter-persistence` |
| PII at-rest encryption (AES-GCM converter + key provider) | `starter-persistence` / `starter-crypto` |
| Object storage (MinIO put/get + pre-signed URLs) | `starter-storage` |
| Resilience4j defaults (circuit breaker/retry/bulkhead + Feign base) | `starter-resilience` |
| Shared OpenAPI/Springdoc config | `starter-api` / `starter-openapi` |
| HTTP `Idempotency-Key` handling on POST | `starter-api` / `starter-inbox` |
| Domain audit logging | `platform-core/audit` + `starter-audit` |
| Test support (Testcontainers fixtures) | `platform-test` |

Before building any of the above inside a service, check whether the platform module now exists -
this list changes as new sprints ship. Full detail:
[Platform Capability Catalog](../architecture/platform-capabilities.md).
