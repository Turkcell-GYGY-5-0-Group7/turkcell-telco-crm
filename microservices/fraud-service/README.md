# fraud-service

| Field | Value |
| --- | --- |
| Port | 9013 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL (fraud-db, primary store, event-emitting service); Redis optional cache for hot per-customer velocity counters, explicitly not source of truth |
| Owning sprint | [Sprint 23](../../docs/tasks/sprint-23-sim-swap-fraud/README.md) |
| Status | Skeleton (schema + aggregates only, no business code) |

**Architecture Mode: CQRS + MEDIATOR** (ADR-004, ADR-029 Decision Section 2)

Rule-based SIM-swap / fraud detection (ADR-029). Consumes existing subscription-service domain
events (`msisdn.allocated.v1`, `msisdn.released.v1`, `subscription.activated.v1`,
`subscription.suspended.v1`) via the inbox, evaluates threshold-based rules over a rolling time
window, and publishes `fraud.signal-raised.v1` / `fraud.case-opened.v1` / `fraud.case-resolved.v1`
via the outbox. This is the pragmatic, rule-based MVP authorized by ADR-029 - **not** the
streaming/lakehouse/ML version of TELCO-CRM-ADVANCED.md Section 4.4 (deferred to a later ADR).

## Bounded context (ADR-029 Section 1)

fraud-service is **read-only relative to subscription-service**: it reacts to subscription-service's
already-published events via the inbox and **never accesses `subscription-db` directly** (ADR-006
cross-service data rule). Fraud-detection logic lives here, in a separate, lightweight service,
rather than being bolted onto subscription-service - keeping subscription-service's operational,
latency-sensitive, audit-mandated lifecycle state machine free of a security-analytics workload with
a different scaling and retention profile.

## Infrastructure profile (ADR-006)

fraud-service owns a new `fraud-db` (PostgreSQL 17, database-per-service) - a mandatory relational
primary store because fraud-service emits domain events (ADR-006's event-emitting-services rule).
Redis MAY later be added as a cache for hot per-customer velocity counters, **explicitly not** as the
source of truth (design-note.md Section 8); no cache is present in this scaffold.

## Scope of this build (Sprint 23 Feature 23.1)

This is a schema-and-skeleton scaffold only:

- `fraud-db` Flyway schema (`fraud_rule`, `msisdn_lifecycle_signal`, `fraud_signal`, `fraud_case`)
  plus platform outbox/inbox tables, seeded with the three ADR-029 default `FraudRule` rows
  (`RAPID_SIM_SWAP`, `MSISDN_CHURN_VELOCITY`, `SUSPEND_REACTIVATE_VELOCITY`).
- Bare `MsisdnLifecycleSignal` / `FraudRule` / `FraudSignal` / `FraudCase` JPA aggregates (fields and
  column mappings only) plus their Spring Data repositories, including the rolling-window query
  methods rule evaluation (23.2) builds on.
- No rule-evaluation logic, no command/query handlers, no API, and no eventing/outbox-inbox wiring -
  see [23.2](../../docs/tasks/sprint-23-sim-swap-fraud/23.2-rule-evaluation-and-inbox.md),
  [23.3](../../docs/tasks/sprint-23-sim-swap-fraud/23.3-fraud-case-api.md), and
  [23.4](../../docs/tasks/sprint-23-sim-swap-fraud/23.4-fraud-outbox-events.md).

## Platform wiring

- Platform starters only (ADR-018): `starter-api`, `starter-security`, `starter-observability`,
  `starter-outbox` (inherited from `domain-services-parent`) plus `starter-mediator` and
  `starter-inbox` (declared directly in `pom.xml`). Never depends on `platform-core` modules
  directly.
- Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
  bootstrap (`microservices/configs/fraud-service/`). Reuse platform capabilities first
  ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl fraud-service spring-boot:run
curl localhost:9013/actuator/health
```

Contract: [fraud-service](../../docs/api-contracts/fraud-service.md).
