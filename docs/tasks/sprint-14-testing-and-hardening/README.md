# Sprint 14 - Testing and Hardening

| Status | Progress | Last updated |
| --- | --- | --- |
| IN PROGRESS | 1/3 | 2026-07-04 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Raise the platform to production quality: full acceptance-criteria validation, contract testing of
event schemas and APIs, security hardening (PII-at-rest encryption coverage, audit-log completeness,
PII telemetry masking, mTLS posture decision), and performance validation against the NFR targets.

Covers NFR-01, NFR-02, NFR-06, NFR-12, NFR-16, NFR-17 and final validation of AC-01/02/03.

## Included Epics

- Epic 14: Quality, Security, and Performance Hardening

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 14.1 | Acceptance and End-to-End Testing | IN PROGRESS | [14.1-acceptance-and-end-to-end-testing.md](14.1-acceptance-and-end-to-end-testing.md) |
| 14.2 | Security Hardening | DONE | [14.2-security-hardening.md](14.2-security-hardening.md) |
| 14.3 | Performance Validation | TODO | [14.3-performance-validation.md](14.3-performance-validation.md) |

Sub-status (14.1): 14.1.2 contract tests DONE, 14.1.3 coverage gate DONE. 14.1.1 acceptance E2E is
IN PROGRESS: infra (Docker Compose `apps` profile for all 10 domain services incl. `mongo` for
notification-service, Makefile targets, 10 Debezium connectors, `.github/workflows/acceptance.yml`)
and the `microservices/acceptance-tests` suite (AC-01 incl. compensation, AC-02, AC-03, gateway-driven,
real Keycloak `SUBSCRIBER` user) are both built and compile clean; `docker compose config` validated
for both the `apps` and full `auth+platform+apps` profile combinations. NOT yet run against a live
stack (nobody has booted Docker this session) and NOT yet wired to actually pass in CI - that is the
remaining work before 14.1.1 can move to DONE. Building the suite honestly (real IdP token, real
gateway calls) surfaced and fixed 8 real cross-service bugs along the way - see
[`lessons.md`](../lessons.md) 2026-07-04 entries for the full list. One gap was found and ruled on but NOT implemented this session: `customer-service` never links a
self-registered `customerId` to the caller's Keycloak subject, so no "view my own resource" ownership
check anywhere in the platform (subscriptions, invoices, quota/usage, tickets, notifications) can be
satisfied by a real end-user token today; the suite falls back to an ADMIN token for those specific
reads until this is resolved. Full tech-lead ruling, scope, and execution order:
[14.1.1-identity-linkage-gap-ruling.md](14.1.1-identity-linkage-gap-ruling.md). That ruling also
flagged an independently urgent, small-scope finding: `customer-service`'s `CustomerController` has
no `@PreAuthorize`/ownership check at all on `GET`/`PUT /api/v1/customers/{id}` — any authenticated
caller can currently read or overwrite any other customer's profile by ID (broken access control,
OWASP A01). Unlike the linkage redesign, this is a same-session-sized fix and should not wait for the
full ruling to be scheduled.
14.2 all four subtasks complete (audits PASS; payment + customer-address audit-log gaps fixed and
verified; a related `AuditLogWriter` UUID-parsing crash found in 4 of those services was also fixed
this session).

## Sprint Deliverables

- Automated acceptance suite (AC-01/02/03 incl. compensation), event/API contract tests, and a
  coverage gate in CI.
- Security hardening: verified PII encryption at rest, PII telemetry masking, audit-log completeness,
  and a documented mTLS/security posture.
- Performance validation against NFR-01 (p95 latency) and NFR-02 (bill-run throughput).

## Exit Criteria

- All MVP acceptance criteria pass end to end in CI; contract tests guard event/API boundaries.
- PII is encrypted at rest and masked in telemetry everywhere; audit logging is complete in the four
  mandated services; no high-severity security findings remain.
- NFR-01 and NFR-02 targets are met and recorded.
</content>
