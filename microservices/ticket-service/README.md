# ticket-service

| Field | Value |
| --- | --- |
| Port | 9010 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL |
| Owning sprint | [Sprint 12](../../docs/tasks/sprint-12-notifications-and-ticketing/README.md) |
| Status | Skeleton (no business code) |

Customer support ticketing: create, route, escalate, and close tickets with full SLA tracking.
Commands and queries dispatched via InProcessMediator; events emitted through the transactional outbox.
Contract: [ticket-service](../../docs/api-contracts/ticket-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl ticket-service spring-boot:run
```
