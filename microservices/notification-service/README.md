# notification-service

| Field | Value |
| --- | --- |
| Port | 9009 |
| Architecture Mode (ADR-004) | Simple Service Layer |
| Infrastructure Profile (ADR-006) | MongoDB (approved exception) + PostgreSQL outbox/inbox |
| Owning sprint | [Sprint 12](../../docs/tasks/sprint-12-notifications-and-ticketing/README.md) |
| Status | Skeleton (no business code) |

Multi-channel templated notifications consuming domain events; respects preferences. Documents/history
live in MongoDB; the single `notification.dispatched.v1` event emits via a co-located Postgres outbox
(ADR-006). Contract: [notification-service](../../docs/api-contracts/notification-service.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl notification-service spring-boot:run
```
