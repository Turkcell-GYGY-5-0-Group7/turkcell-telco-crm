# customer-service

| Field | Value |
| --- | --- |
| Port | 9002 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL + MinIO (KYC documents) |
| Owning sprint | [Sprint 06](../../docs/tasks/sprint-06-customer-domain/README.md) |
| Status | Skeleton (no business code) |

Customer master record: registration, KYC, address/document management, PII encryption, soft-delete.
Contract: [customer-service](../../docs/api-contracts/customer-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl customer-service spring-boot:run
```
