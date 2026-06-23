# Platform Capability Catalog (reuse-before-build)

| Field | Value |
| --- | --- |
| Audience | Anyone (human or agent) building a microservice |
| Authority | ADR-007 (platform library), ADR-018 (starter dependency), `platform/PLATFORM-SPEC.md` |
| Rule | **Reuse before build.** If a capability is listed here, do NOT re-implement it. |
| Last updated | 2026-06-23 |

This is the consumer-facing index of what the platform already provides. `PLATFORM-SPEC.md` is the
builder's contract (how the platform is implemented); this is the developer's "what can I reuse, and
how do I import it" guide. The canonical worked example is
[`microservices/reference-service`](../../microservices/reference-service) - copy its shape.

Services depend ONLY on platform **starters**, never on `platform-core` directly (ADR-018). The types
below come transitively through the starters.

## 1. Available now - do NOT reinvent these

### API contract (via `starter-api`)
| Capability | Import | Use for |
| --- | --- | --- |
| `ApiResult<T>` | `com.telco.platform.common.api.ApiResult` | Every external response wrapper (NFR-14). |
| `ApiError`, `ApiMeta` | `com.telco.platform.common.api.*` | Error body + observability meta (auto-populated). |
| `PageResult<T>` | `com.telco.platform.common.api.PageResult` | Offset pagination response. |
| `CursorPage<T>` | `com.telco.platform.common.api.CursorPage` | Cursor pagination for high-volume reads. |
| `GlobalExceptionHandler` | (auto, starter-api) | Maps exceptions -> `ApiResult.failure` + HTTP status. Do not write your own. |

### Errors and validation (via `starter-api` / platform-common)
| Capability | Import | Use for |
| --- | --- | --- |
| `PlatformException` + subtypes | `com.telco.platform.common.exception.*` | Throw `ResourceNotFoundException`, `ValidationException`, `ConflictException`, `BusinessRuleException`, `AccessDeniedException`, `UnauthenticatedException`, `DependencyFailureException` - each maps to the right HTTP status. |
| `ErrorCode` / `CommonErrorCode` | `com.telco.platform.common.exception.*` | Stable error codes; extend per service with your own `ErrorCode` enum. |

### Request context (via platform-common / `starter-security` / `starter-observability`)
| Capability | Import | Use for |
| --- | --- | --- |
| `UserContext`, `UserContextHolder` | `com.telco.platform.common.context.*` | Current user id/roles/tenant. |
| `CurrentUserProvider` | `com.telco.platform.common.context.CurrentUserProvider` | Inject to read the current user. |
| `CorrelationContext(Holder)`, `CorrelationConstants` | `com.telco.platform.common.context.*` | traceId/correlationId + header names. |

### CQRS + Mediator (via `starter-mediator`)
| Capability | Import | Use for |
| --- | --- | --- |
| `Command<R>`, `Query<R>`, `Event`, `*Handler`, `Unit` | `com.telco.platform.cqrs.*` | Define commands/queries/events + handlers. |
| `Mediator` | `com.telco.platform.mediator.Mediator` | `send` / `query` / `publish`. Controllers call this, not services directly. |
| Pipeline behaviors | (auto) | Validation, Authorization, Logging, Performance, Transaction, Inbox - all applied automatically. |
| `AuthorizationRule`, `NotLoggable` | `com.telco.platform.mediator.behavior.support.*` | Add authz rules; opt a request out of logging. |

### Eventing (via `starter-outbox` / `starter-inbox` / event-contracts)
| Capability | Import | Use for |
| --- | --- | --- |
| `OutboxService` | `com.telco.platform.outbox.OutboxService` | `publish(aggregateType, aggregateId, eventType, payload)` - atomic with your DB write. Never call Kafka directly. |
| `InboxService.firstSeen(...)`, `IdempotentRequest`, `InboxBehavior` | `com.telco.platform.inbox.*` | Idempotent event consumption. |
| `EventEnvelope` + `*V1` Avro records | `com.telco.platform.events.*` | Versioned `domain.event.v1` contracts (ADR-019). |

### Security (via `starter-security`)
| Capability | Import | Use for |
| --- | --- | --- |
| JWT validation + gateway-trust | (auto) | Validates the Keycloak JWT or trusts gateway headers (ADR-011). Services do not issue tokens. |

### Observability (via `starter-observability`)
| Capability | Import | Use for |
| --- | --- | --- |
| `CorrelationFilter`, MDC wiring | (auto) | traceId/correlationId on every request + structured logs. |
| `@Sensitive`, `PiiMasker`, `MaskStrategy` | `com.telco.platform.common.masking.*` | Mask PII in logs/telemetry (ADR-021). |

### Build
| Capability | Use for |
| --- | --- |
| `platform-bom` | Inherit ALL dependency versions. Never hardcode a version in a service POM (ADR-003, ADR-020). |

## 2. New-service checklist

1. Copy [`microservices/service-template`](../../microservices/service-template) (ADR-017).
2. Add mandatory starters: `starter-api`, `starter-security`, `starter-observability`. Add optional
   `starter-mediator`, `starter-outbox`, `starter-inbox` as the domain needs.
3. Declare in the service README/CLAUDE.md: the **Architecture Mode** (ADR-004) and the
   **Infrastructure Profile** (ADR-006) - see [service-catalog](service-catalog.md).
4. Reuse the Section 1 capabilities. Do not re-create `ApiResult`, error types, context, pagination,
   correlation, or masking.
5. Flyway: add `classpath:db/migration,classpath:db/migration/platform` so platform tables (outbox/
   inbox) apply.
6. Tests: Testcontainers integration tests (ADR-013).

## 3. Planned - not yet available (coordinate with platform-engineer; do not hand-roll permanently)

These cross-cutting capabilities are identified gaps. Until they ship as platform modules, a service
may implement locally, but flag it so it migrates to the platform (avoid divergence):

| Planned capability | Likely home |
| --- | --- |
| BaseEntity + JPA auditing + soft-delete | `starter-persistence` |
| PII at-rest encryption (AES-GCM converter + key provider) | `starter-persistence`/`starter-crypto` |
| Object storage (MinIO put/get + pre-signed URLs) | `starter-storage` |
| Resilience4j defaults (CB/retry/bulkhead + Feign base) | `starter-resilience` |
| Shared OpenAPI/Springdoc config (Keycloak bearer scheme, ApiResult schema) | `starter-api`/`starter-openapi` |
| HTTP Idempotency-Key handling on POST | `starter-api`/`starter-inbox` |
| Domain audit logging (NFR-12) | `platform-core/audit` + `starter-audit` (`@Audited` + AuditBehavior); ADR-023 |
| Avro <-> outbox bridge | `EventEnvelopeFactory` in platform-event-contracts + Avro serializer in starter-outbox (outbox payload -> bytea) |
| Keycloak JWKS validation | `starter-security` via Spring `oauth2-resource-server` (jwks-uri); `JwtService.issue()` retired |
| Test support (Testcontainers fixtures) | `platform-test` |

Before building any of the above inside a service, check whether the platform module now exists.
