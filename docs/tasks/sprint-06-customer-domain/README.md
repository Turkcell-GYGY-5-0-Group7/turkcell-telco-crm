# Sprint 06 - Customer Domain

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 4/4 | 2026-06-26 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Build customer-service (9002): individual customer registration with TCKN validation, KYC workflow
(PENDING -> ACTIVE/REJECTED), address and document management, soft-delete (KVKK/GDPR), PII
encryption at rest, and domain events. This is the first business domain and a prerequisite for
ordering (Sprint 08) and onboarding (Sprint 09).

Covers FR-01, FR-02, FR-03, FR-04.

## Included Epics

- Epic 6: Customer Management (customer-service)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 6.1 | Scaffold and Schema | DONE | [6.1-scaffold-and-schema.md](6.1-scaffold-and-schema.md) |
| 6.2 | Domain and Persistence | DONE | [6.2-domain-and-persistence.md](6.2-domain-and-persistence.md) |
| 6.3 | Application (Commands, Queries, Endpoints) | DONE | [6.3-application-commands-queries-endpoints.md](6.3-application-commands-queries-endpoints.md) |
| 6.4 | Tests | DONE | [6.4-tests.md](6.4-tests.md) |

## Sprint Deliverables

- customer-service (9002): registration with TCKN validation, KYC workflow with events, address and
  document management, PII AES-GCM encryption, soft-delete, audit logging, and integration tests.

## Exit Criteria

- A customer can register (PENDING), upload a KYC document, and be approved (ACTIVE) or rejected,
  with `customer.registered.v1` and `customer.kyc-approved/rejected.v1` published via the outbox.
- Identity numbers are encrypted at rest and masked in responses/logs; soft-delete preserves the row.
- FR-01, FR-02, FR-03, FR-04 pass; AC-01 steps 1-3 are satisfied (full AC-01 validated in Sprint 09).
</content>
