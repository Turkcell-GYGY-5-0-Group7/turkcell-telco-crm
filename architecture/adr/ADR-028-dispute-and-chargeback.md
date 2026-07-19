# ADR-028 Dispute and Chargeback

Status: Accepted
Date: 2026-07-11
Ratified: 2026-07-17 (tech-lead; see Sections 4/5/6 amendment notes below)

---

## Context

The platform has no dispute/chargeback aggregate or event today. The closest existing analogues are
`payment.refunded.v1` (payment-service compensation) and `invoice.overdue.v1` (billing-service
dunning), neither of which models a contested charge with a review workflow, evidence, and a possible
provisional hold. This ADR defines where the Dispute aggregate lives and how a provisional credit
interacts with billing-service's and payment-service's financial systems of record without corrupting
either.

## Decision

### 1. Service boundary

A new **dispute-service** (proposed port 9012) owns the Dispute workflow. The Dispute aggregate does
**not** live inside billing-service or payment-service.

Three options were evaluated:

- Inside billing-service: rejected - a chargeback frequently originates PSP-side, before any
  invoice-side action; billing-service would have to reach into payment/PSP-shaped data it does not
  own.
- Inside payment-service: rejected - an invoice-originated dispute (against a specific InvoiceLine) is
  billing-shaped data payment-service does not own.
- New dispute-service coordinating both (chosen): the workflow spans two systems of record (Invoice in
  billing-service, Payment/chargeback in payment-service) and must coordinate a provisional hold and a
  possible financial resolution across them, without either service losing sole write ownership of its
  own ledger - the ADR-006 cross-service data rule requires coordination via events/REST, never shared
  DB access.

### 2. Architecture mode (ADR-004)

**Domain Orchestration.** ADR-004's criteria are squarely met: multiple aggregates involved in a
single workflow (Dispute, Invoice, Payment), saga-like process with compensation-shaped branches
(RESOLVED_CUSTOMER triggers a real financial action; RESOLVED_MERCHANT does not), event-driven
coordination required. This is the same reasoning that already places order-service, billing-service,
and payment-service in Domain Orchestration mode. dispute-service owns the workflow state (Dispute)
but never owns Invoice or Payment rows directly.

dispute-service is **audit-mandated** given its financial impact, joining identity, customer,
subscription, and payment as audit-mandated services (ADR-021, NFR-12).

### 3. Data ownership (ADR-006)

dispute-service owns a new `dispute-db` (PostgreSQL 17, database-per-service). Aggregates: Dispute,
DisputeEvidence (object references via MinIO, mirroring customer-service's KYC-document pattern),
DisputeStateHistory. billing-service and payment-service each own a small schema addition under their
own migrations and their own write ownership (`Invoice.disputeStatus` + a credit/adjustment
InvoiceLine type in billing-service; a disputed flag on Payment/PaymentAttempt in payment-service).
dispute-service never writes to `billing-db` or `payment-db` directly; all cross-service coordination
is via the outbox/inbox event pattern (ADR-009, ADR-019).

### 4. State machine

```text
OPENED
  -> UNDER_REVIEW
       -> EVIDENCE_SUBMITTED -> UNDER_REVIEW   (loop: more evidence may follow more review)
       -> RESOLVED_CUSTOMER                     (dispute uphold: credit/refund issued)
       -> RESOLVED_MERCHANT                     (dispute rejected: invoice/payment stands)
OPENED | UNDER_REVIEW
       -> WITHDRAWN                             (customer withdraws the dispute)
RESOLVED_CUSTOMER | RESOLVED_MERCHANT | WITHDRAWN
       -> CLOSED                                (terminal, after settlement confirmed)
```

(Ratification note, 2026-07-17: this diagram was replaced at ratification with the equivalent tree
form from `docs/tasks/sprint-22-dispute-chargeback/design-note.md` Section 4 - the original terse
ASCII rendering was ambiguous, read in isolation, about which states `RESOLVED_CUSTOMER` and
`RESOLVED_MERCHANT` transition to next; the two documents were never in actual disagreement, only
this ADR's rendering was unclear.)

SLA-expiry handling is **not** a dispute-service state: ticket-service already owns SLA tracking
(Ticket/TicketComment/SLA, `ticket.sla-breached.v1`). ticket-service consumes `dispute.opened.v1` and
auto-opens a linked ticket for agent review, reusing existing SLA/assignment machinery instead of
duplicating it in dispute-service (reuse-before-build).

### 5. Provisional credit without corrupting billing-service's system of record

A dispute under review must not silently mutate billing-service's ledger:

- On entering `UNDER_REVIEW`, dispute-service publishes `dispute.opened.v1`. billing-service's inbox
  consumer sets `Invoice.disputeStatus = ON_HOLD` - a hold flag only, invoice total untouched, invoice
  excluded from the overdue/dunning check while held. This is the "provisional" part: pausing
  collections, not creating money. payment-service's inbox consumer marks the related payment as
  disputed, suppressing auto-retry/auto-settlement.
- Only `dispute.resolved-customer.v1` triggers a **real** financial action: if the invoice is unpaid,
  billing-service's own inbox consumer adds a real `ADJUSTMENT`/`CREDIT` InvoiceLine for the resolution
  amount, but only after checking that `Invoice.disputeStatus == ON_HOLD` for the referenced invoice -
  if the hold is not currently set (already cleared, already resolved, or never held), the consumer
  MUST no-op rather than append a second adjustment line, so that inbox dedup is not the only guard
  against a duplicate financial adjustment if it is ever bypassed (bug, manual replay, DLQ redrive); if
  already paid, payment-service's own inbox consumer issues a refund by dispatching an internal
  `Mediator` command inside its own inbox consumer - reusing the existing
  `POST /api/v1/payments/{id}/refund` capability's underlying command handler in-process, never via a
  synchronous cross-service HTTP call from dispute-service (which would violate ADR-006's no-shared-
  access/events-only rule) - publishing the existing `payment.refunded.v1` event. Track 2 reuses
  payment-service's refund machinery rather than building a parallel one.
- `dispute.resolved-merchant.v1` simply clears the hold (no financial change).

### 6. New events (producer: dispute-service)

`dispute.opened.v1`, `dispute.evidence-submitted.v1`, `dispute.resolved-customer.v1`,
`dispute.resolved-merchant.v1`, `dispute.withdrawn.v1`, `dispute.closed.v1`. Consumers:
billing-service, payment-service, ticket-service (auto-ticket), notification-service.

All six `dispute.*.v1` events use `aggregate_id = disputeId` (the platform-standard outbox convention
every other service follows), which guarantees all events for a given dispute land on the same Kafka
partition and are therefore delivered and processed in order - a load-bearing property the
provisional-hold invariant in Section 5 depends on.

## Consequences

### Positive

- Neither billing-service nor payment-service loses sole ownership of its financial ledger; the
  provisional hold cannot corrupt either system of record.
- Reuses ticket-service's SLA machinery and payment-service's refund capability instead of duplicating
  either.
- Matches the platform's existing Domain Orchestration pattern (order-service) for a genuinely
  cross-service, saga-like workflow.

### Negative

- One more service to operate; a dispute resolution now touches up to three services
  (dispute/billing/payment) via eventually-consistent events rather than a single transaction.
- billing-service and payment-service each need a small, real schema/behavior addition (hold flag,
  credit-adjustment line type) - scoped as their own feature tasks in the sprint, not owned by
  dispute-service.

## Alternatives Considered

### Dispute inside billing-service

Rejected - a chargeback frequently originates PSP-side; billing-service would need PSP/payment-shaped
data it does not own.

### Dispute inside payment-service

Rejected - an invoice-originated dispute is billing-shaped data payment-service does not own.

### A generic "adjustment" event with no dedicated dispute aggregate

Rejected - loses the review/evidence workflow and state history required for a defensible dispute
resolution audit trail.

## Related ADRs

* ADR-004 Architecture Style (mode selection: Domain Orchestration)
* ADR-006 Database Strategy (database-per-service, cross-service data rule)
* ADR-009 Event Driven Architecture / ADR-019 Event Contract and Schema Governance
* ADR-021 PII and Data Masking Strategy (audit-mandated status)
* ADR-017 Service Template Standard
