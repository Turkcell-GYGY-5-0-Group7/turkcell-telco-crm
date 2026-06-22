# Sprint 05 - Security and Identity

## Objective

Build identity-service (9001): user/role/permission management, login issuing JWT access and refresh
tokens, refresh-token rotation with Redis blacklist and reuse detection, RBAC enforced on admin
endpoints, and an audit log. With the gateway (Sprint 04) already validating JWT and propagating
identity headers, this sprint completes authenticated, authorized access for every later service.

Covers FR-IAM-01, FR-IAM-04, FR-IAM-05 (FR-IAM-02/03 delivered at the gateway in Sprint 04).

## Included Epics

- Epic 5: Identity and Access Management (identity-service)

## Service setup note

identity-service is a CQRS + MEDIATOR service created from the service template (ADR-017), depending
only on starters. Its CLAUDE.md declares the mode; it owns its `identity` database; audit logging is
mandatory. Tasks below assume the template instance already builds and registers with discovery.

## Features (one file per top-level task)

| ID | Feature | File |
| --- | --- | --- |
| 5.1 | Service Scaffold and Schema | [5.1-service-scaffold-and-schema.md](5.1-service-scaffold-and-schema.md) |
| 5.2 | Domain and Persistence | [5.2-domain-and-persistence.md](5.2-domain-and-persistence.md) |
| 5.3 | Authentication | [5.3-authentication.md](5.3-authentication.md) |
| 5.4 | Refresh-Token Rotation and Reuse Detection | [5.4-refresh-token-rotation-and-reuse-detection.md](5.4-refresh-token-rotation-and-reuse-detection.md) |
| 5.5 | User Management and RBAC | [5.5-user-management-and-rbac.md](5.5-user-management-and-rbac.md) |
| 5.6 | Audit Logging | [5.6-audit-logging.md](5.6-audit-logging.md) |
| 5.7 | Tests | [5.7-tests.md](5.7-tests.md) |

## Sprint Deliverables

- identity-service (9001) with user/role/permission management, login and refresh endpoints, JWT
  issuance, refresh rotation + Redis blacklist + reuse detection, RBAC on admin endpoints, audit log,
  and seeded roles/admin.
- Integration tests against Testcontainers Postgres + Redis.

## Exit Criteria

- A user can log in through the gateway, receive tokens, refresh them with rotation, and be revoked
  on reuse; admin endpoints reject non-admin tokens.
- FR-IAM-01, FR-IAM-04, FR-IAM-05 pass; combined with Sprint 04, all FR-IAM requirements are met.
- Audit rows are written for identity state changes; the reusable audit pattern is available to
  later services.
</content>
