# identity-service

| Field | Value |
| --- | --- |
| Port | 9001 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL |
| Owning sprint | [Sprint 05](../../docs/tasks/sprint-05-security-and-identity/README.md) |
| Status | Skeleton (no business code) |

User/role/permission management; delegates authentication and token issuance to Keycloak (ADR-011).
Contract: [identity-service](../../docs/api-contracts/identity-service.md). Auth model:
[keycloak-and-auth](../../docs/architecture/keycloak-and-auth.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Add mode-specific starters (mediator/outbox) and persistence during implementation - reuse
platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl identity-service spring-boot:run
```
