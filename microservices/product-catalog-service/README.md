# product-catalog-service

| Field | Value |
| --- | --- |
| Port | 9003 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL + Redis (cache-aside); MongoDB read-projection post-MVP |
| Owning sprint | [Sprint 07](../../docs/tasks/sprint-07-product-catalog-domain/README.md) |
| Status | Skeleton (no business code) |

Tariffs, addons, VAS; read-heavy with Redis cache-aside and versioned tariff changes.
Contract: [product-catalog-service](../../docs/api-contracts/product-catalog-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl product-catalog-service spring-boot:run
```
