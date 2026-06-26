# Sprint 05 - Security and Identity

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 7/7 | 2026-06-26 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Stand up authenticated, authorized access for every later service. Per **ADR-011, Keycloak is the
identity provider and token issuer**: it owns login, JWT issuance, refresh-token rotation, and reuse
detection as realm features. This sprint therefore (1) configures the `telco-crm` Keycloak realm
(roles, clients, token/refresh policy, role->claim mapping), (2) builds identity-service (9001) as the
**user/role/permission management** service that administers users via the Keycloak Admin API, owns a
domain projection of identity and app-specific authorization data, and writes an audit log, and (3)
confirms the gateway (Sprint 04) validates Keycloak JWTs via JWKS and propagates identity headers.

Covers FR-IAM-01, FR-IAM-04, FR-IAM-05 (FR-IAM-02/03 delivered at the gateway in Sprint 04).

> **AUTH RECONCILIATION (ADR-011).** identity-service does NOT mint or refresh JWTs. Features 5.3
> (Authentication) and 5.4 (Refresh-Token Rotation and Reuse Detection) are realized by **Keycloak
> realm configuration**, not custom token code. Read those feature files as "configure and verify the
> Keycloak realm flow + wire service-side validation", not "implement a custom JWT issuer". See
> [`docs/architecture/keycloak-and-auth.md`](../../architecture/keycloak-and-auth.md).

## Included Epics

- Epic 5: Identity and Access Management (identity-service + Keycloak realm)

## Service setup note

identity-service is a CQRS + MEDIATOR service created from the service template (ADR-017), depending
only on starters. Its CLAUDE.md declares the mode and its Infrastructure Profile (PostgreSQL); it owns
its `identity` database; audit logging is mandatory. Tasks below assume the template instance already
builds and registers with discovery, and that the Keycloak realm from `infra/docker/keycloak` is
running.

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 5.1 | Service Scaffold and Schema | DONE | [5.1-service-scaffold-and-schema.md](5.1-service-scaffold-and-schema.md) |
| 5.2 | Domain and Persistence | DONE | [5.2-domain-and-persistence.md](5.2-domain-and-persistence.md) |
| 5.3 | Authentication | DONE | [5.3-authentication.md](5.3-authentication.md) |
| 5.4 | Refresh-Token Rotation and Reuse Detection | DONE | [5.4-refresh-token-rotation-and-reuse-detection.md](5.4-refresh-token-rotation-and-reuse-detection.md) |
| 5.5 | User Management and RBAC | DONE | [5.5-user-management-and-rbac.md](5.5-user-management-and-rbac.md) |
| 5.6 | Audit Logging | DONE | [5.6-audit-logging.md](5.6-audit-logging.md) |
| 5.7 | Tests | DONE | [5.7-tests.md](5.7-tests.md) |

## Sprint Deliverables

- Configured `telco-crm` Keycloak realm: roles, web/gateway clients, token + refresh-rotation +
  reuse-detection policy, and role->`roles` claim mapping.
- identity-service (9001) with user/role/permission management via the Keycloak Admin API, a domain
  projection of identity, RBAC on admin endpoints, audit log, and seeded roles/admin.
- Gateway validation of Keycloak JWTs (JWKS) and identity-header propagation, verified end to end.
- Integration tests against Testcontainers Postgres + Keycloak.

## Exit Criteria

- A user can log in through the gateway, receive tokens, refresh them with rotation, and be revoked
  on reuse; admin endpoints reject non-admin tokens.
- FR-IAM-01, FR-IAM-04, FR-IAM-05 pass; combined with Sprint 04, all FR-IAM requirements are met.
- Audit rows are written for identity state changes; the reusable audit pattern is available to
  later services.
</content>
