# Sprint 22 - Invoice Dispute / Chargeback (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/6 | 2026-07-11 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. It is documented now (design pass + Proposed ADR) and built later.
> Feature subtask files will be authored when the sprint is scheduled.

## Objective

Deliver an invoice dispute / PSP chargeback workflow: a new `dispute-service` (Domain Orchestration
per ADR-004) that coordinates a provisional hold on the disputed invoice and, on resolution, a real
credit or refund - without billing-service or payment-service losing sole write ownership of their own
ledgers. Built per ADR-028 (new service, database-per-service, event-driven coordination) and reusing
ticket-service's existing SLA machinery and payment-service's existing refund capability.

## Included Epics

- Epic 22: Invoice Dispute and Chargeback

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 22.1 | dispute-service scaffold and schema (ADR-017 template, `dispute-db`, Dispute/DisputeEvidence/DisputeStateHistory, audit-mandated) | TODO | [22.1-dispute-service-scaffold-and-schema.md](22.1-dispute-service-scaffold-and-schema.md) |
| 22.2 | Dispute state machine and orchestration (application services coordinating billing/payment via events) | TODO | [22.2-dispute-state-machine-and-orchestration.md](22.2-dispute-state-machine-and-orchestration.md) |
| 22.3 | Dispute API (open, submit evidence, resolve) + evidence upload (MinIO) | TODO | [22.3-dispute-api-and-evidence-upload.md](22.3-dispute-api-and-evidence-upload.md) |
| 22.4 | billing-service extension: `Invoice.disputeStatus` hold + credit/adjustment InvoiceLine | TODO | [22.4-billing-service-dispute-extension.md](22.4-billing-service-dispute-extension.md) |
| 22.5 | payment-service extension: dispute-aware hold + reuse of existing refund flow | TODO | [22.5-payment-service-dispute-extension.md](22.5-payment-service-dispute-extension.md) |
| 22.6 | Dispute eventing (outbox) + ticket-service auto-ticket consumer + tests | TODO | [22.6-dispute-eventing-and-ticket-integration.md](22.6-dispute-eventing-and-ticket-integration.md) |

## Sprint Deliverables

- `dispute-service` (new, port 9012 proposed), Domain Orchestration mode, with its own `dispute-db`.
- Provisional hold on the disputed invoice (`disputeStatus=ON_HOLD`) with no financial ledger mutation
  until resolution.
- Real financial resolution reuses billing-service's new credit-adjustment line or payment-service's
  existing refund endpoint, depending on where the money currently sits.
- ticket-service auto-opens a linked ticket on `dispute.opened.v1`, reusing its existing SLA machinery.

## Exit Criteria

- A dispute opened against a paid invoice results in either a real credit adjustment or a real refund
  on `RESOLVED_CUSTOMER`, and no financial change on `RESOLVED_MERCHANT`.
- The disputed invoice is excluded from overdue/dunning while `ON_HOLD`, with no invoice-total
  mutation before resolution.
- No shared database access between dispute-service, billing-service, and payment-service (ADR-006
  verified); all coordination is via outbox/inbox events.

## References

- [ADR-028 Dispute and Chargeback](../../../architecture/adr/ADR-028-dispute-and-chargeback.md)
- [design-note.md](design-note.md)
- [service-catalog.md](../../architecture/service-catalog.md)
- [event-catalog.md](../../architecture/event-catalog.md)
