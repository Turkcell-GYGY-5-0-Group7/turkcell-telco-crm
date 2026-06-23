# usage-service

| Field | Value |
| --- | --- |
| Port | 9006 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL + Redis (near-real-time quota) |
| Owning sprint | [Sprint 10](../../docs/tasks/sprint-10-usage-metering/README.md) |
| Status | Skeleton (no business code) |

CDR ingestion, quota decrement, threshold/overage events; write-heavy.
Contract: [usage-service](../../docs/api-contracts/usage-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl usage-service spring-boot:run
```
