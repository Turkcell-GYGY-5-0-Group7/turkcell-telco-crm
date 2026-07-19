# dispute-service

| Field | Value |
| --- | --- |
| Port | 9012 |
| Architecture Mode (ADR-004) | Domain Orchestration |
| Infrastructure Profile (ADR-006) | PostgreSQL (`dispute-db`) + MinIO (dispute evidence objects, wired in Feature 22.3 - not yet present) |
| Owning sprint | [Sprint 22](../../docs/tasks/sprint-22-dispute-chargeback/README.md) |
| Status | Feature 22.1/22.2: scaffold, schema, state machine, commands/handlers, event contracts. No HTTP API yet (22.3), no billing/payment/ticket-service consumers yet (22.4/22.5/22.6). |

Coordinates a provisional hold on a disputed invoice/payment and, on resolution, a real credit or
refund - without billing-service or payment-service losing sole write ownership of their own ledgers
(ADR-028). dispute-service owns the `Dispute`/`DisputeEvidence`/`DisputeStateHistory` aggregates in its
own `dispute-db`; it never writes to `billing-db` or `payment-db` directly. All cross-service
coordination is via the outbox/inbox event pattern (ADR-009/019) - dispute-service is an event
**producer only** in this sprint (`starter-inbox` is intentionally not a dependency yet).
Audit-mandated (ADR-021, NFR-12).

Contract: API contract to be authored in Feature 22.3 (`docs/api-contracts/dispute-service.md`).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl dispute-service spring-boot:run
```
