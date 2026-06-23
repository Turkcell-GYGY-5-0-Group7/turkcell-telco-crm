# billing-service

| Field | Value |
| --- | --- |
| Port | 9007 |
| Architecture Mode (ADR-004) | Domain Orchestration |
| Infrastructure Profile (ADR-006) | PostgreSQL + MinIO (invoice PDFs) |
| Owning sprint | [Sprint 11](../../docs/tasks/sprint-11-billing/README.md) |
| Status | Skeleton (no business code) |

Monthly bill-run, invoice line composition, PDF rendering, payment reconciliation.
Contract: [billing-service](../../docs/api-contracts/billing-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl billing-service spring-boot:run
```
