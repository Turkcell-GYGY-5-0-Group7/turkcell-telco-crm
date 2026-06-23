# order-service

| Field | Value |
| --- | --- |
| Port | 9004 |
| Architecture Mode (ADR-004) | Domain Orchestration (saga) |
| Infrastructure Profile (ADR-006) | PostgreSQL |
| Owning sprint | [Sprint 08](../../docs/tasks/sprint-08-order-and-payment/README.md) |
| Status | Skeleton (no business code) |

Order capture and the onboarding saga (customer -> catalog -> payment -> subscription) with
compensation. Contract: [order-service](../../docs/api-contracts/order-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl order-service spring-boot:run
```
