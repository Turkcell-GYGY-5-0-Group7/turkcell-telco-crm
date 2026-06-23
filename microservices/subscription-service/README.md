# subscription-service

| Field | Value |
| --- | --- |
| Port | 9005 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL |
| Owning sprint | [Sprint 09](../../docs/tasks/sprint-09-subscription-and-onboarding-saga/README.md) |
| Status | Skeleton (no business code) |

Subscription lifecycle state machine and atomic MSISDN allocation/release.
Contract: [subscription-service](../../docs/api-contracts/subscription-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl subscription-service spring-boot:run
```
