# payment-service

| Field | Value |
| --- | --- |
| Port | 9008 |
| Architecture Mode (ADR-004) | Domain Orchestration |
| Infrastructure Profile (ADR-006) | PostgreSQL + Redis (idempotency keys) |
| Owning sprint | [Sprint 08](../../docs/tasks/sprint-08-order-and-payment/README.md) |
| Status | Skeleton (no business code) |

Mock PSP charge/refund, mandatory idempotency, timed retry. Audit mandatory.
Contract: [payment-service](../../docs/api-contracts/payment-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl payment-service spring-boot:run
```
