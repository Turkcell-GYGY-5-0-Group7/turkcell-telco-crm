# Sprint 11 - Billing

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 6/6 | 2026-07-01 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Build billing-service (9007): a monthly bill-run that generates invoices for all active postpaid
subscribers, composes invoice lines (monthly fee, addons, overage, VAS, taxes), renders PDFs, emits
invoice events, and reconciles payment. Delivers acceptance criterion AC-02 and the bill-run
performance target (NFR-02).

Covers FR-21, FR-22, FR-23, FR-24.

## Included Epics

- Epic 11: Billing (billing-service)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 11.1 | Scaffold and Schema | DONE | [11.1-scaffold-and-schema.md](11.1-scaffold-and-schema.md) |
| 11.2 | Inputs for Billing | DONE | [11.2-inputs-for-billing.md](11.2-inputs-for-billing.md) |
| 11.3 | Bill-Run and Invoice Generation | DONE | [11.3-bill-run-and-invoice-generation.md](11.3-bill-run-and-invoice-generation.md) |
| 11.4 | Payment Reconciliation | DONE | [11.4-payment-reconciliation.md](11.4-payment-reconciliation.md) |
| 11.5 | Read API | DONE | [11.5-read-api.md](11.5-read-api.md) |
| 11.6 | Tests | DONE | [11.6-tests.md](11.6-tests.md) |

## Sprint Deliverables

- billing-service (9007): event-fed read models, invoice line composition, idempotent monthly
  bill-run with manual trigger, PDF rendering/storage, payment reconciliation, overdue detection,
  invoice read APIs, and integration tests.
- AC-02 integration test.

## Exit Criteria

- AC-02 passes: a bill-run aggregates last-period usage per subscriber, generates a PDF invoice,
  emits `invoice.generated.v1` (notification consumes it in Sprint 12), and on payment emits
  `invoice.paid.v1`.
- The bill-run is idempotent per (subscriber, period) and meets the NFR-02 throughput target in a
  load test. (Idempotency was covered here by `BillingAC02IntegrationTest`; the actual 100K-subscriber
  NFR-02 load test was executed later, in Sprint 14 task 14.3.2 - see
  `sprint-14-testing-and-hardening/14.3.2-bill-run-throughput-report.md` - 100,000 subscribers in
  6m 20s, zero duplicate invoices.)
- FR-21, FR-22, FR-23, FR-24 pass.
</content>
