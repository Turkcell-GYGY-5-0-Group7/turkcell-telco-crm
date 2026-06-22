# Sprint 003 - Identity and Master Data

| Field | Value |
| --- | --- |
| Sprint | 003 |
| Epic | EPIC-005 Identity and Master Data |
| Phase | P1 |
| Status | TODO |
| Progress | 0/5 |
| Started | - |
| Completed | - |

## Goal

Deliver authenticated access plus the customer and catalog master data (Phase P1).

## Tasks

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| T-013 | identity-service: login, JWT issuance, RBAC (FR-IAM-01, FR-IAM-04, FR-IAM-05) | TODO | CQRS + Mediator; consumes starter-security |
| T-014 | api-gateway: JWT validation, X-User-Id/X-User-Roles propagation, rate limiting (FR-IAM-02, FR-IAM-03) | TODO | Spring Cloud Gateway |
| T-015 | customer-service: registration, KYC state machine, soft-delete (FR-01..04) | TODO | CQRS + Mediator |
| T-016 | product-catalog-service: tariff/addon/VAS CRUD with versioning and Redis cache (FR-05..08) | TODO | CQRS + Mediator |
| T-017 | discovery-server and config-server in dev mode (ADR-010) | TODO | Eureka + Spring Cloud Config (dev) |

## Definition of Done

- A customer can register, complete KYC, and browse tariffs through the gateway.
- All services declare an architecture mode (ADR-004) and depend only on starters.

## Dependencies

- BL-01 (local infrastructure stack) for PostgreSQL/Redis.
- Sprint 002 T-012 (service template) recommended before scaffolding.

## Agent Assignments

- Microservice Generator Agent -> service scaffolding
- Security Agent -> identity, gateway
- Architecture Agent -> mode validation (CQRS + Mediator)
- Tech Lead Agent -> final approval
