# Design Note: Invoice Dispute / Chargeback

| Field | Value |
| --- | --- |
| Track | Sprint 22 |
| Status | Draft (input to ADR-028, Proposed) |
| Author | architecture agent |
| Last updated | 2026-07-11 |
| Related | [ADR-028](../../../architecture/adr/ADR-028-dispute-and-chargeback.md), [service-catalog.md](../../architecture/service-catalog.md), [event-catalog.md](../../architecture/event-catalog.md) |

## 1. Problem

There is currently no dispute/chargeback aggregate or event anywhere in the platform. Closest existing
analogues per the event catalog: `payment.refunded.v1` (payment-service compensation) and
`invoice.overdue.v1` (billing-service dunning). Neither models a *contested* charge with a review
workflow, evidence, and a possible provisional credit.

## 2. Where does the Dispute aggregate live?

Three candidates were evaluated:

**A. Inside billing-service (invoice-side).** billing-service owns Invoice/InvoiceLine/BillCycle -
the natural home for "this invoice amount is contested." But a chargeback frequently originates from
the PSP side (a customer disputes a *charge* with their card issuer, which payment-service's PSP
integration surfaces first, before any invoice-side action is taken) - modeling that inside
billing-service would mean billing-service reaching into payment/PSP-shaped data it does not own.

**B. Inside payment-service (chargeback-from-PSP side).** payment-service owns
Payment/PaymentAttempt/Wallet and already has a refund capability
(`POST /api/v1/payments/{id}/refund`, `payment.refunded.v1`). But an invoice-originated dispute (a
customer disputes a specific InvoiceLine, not a specific payment) would need payment-service to reach
into billing-shaped data (invoice lines, bill cycles) it does not own.

**C. A new, small dispute-service coordinating both.** The dispute workflow spans two systems of
record - Invoice (billing-service) and Payment/chargeback (payment-service) - and needs to place a
provisional hold on one, and potentially trigger a real financial action (credit or refund) on either,
depending on where the money currently sits, *without either service losing sole write ownership of
its own ledger* (ADR-006's cross-service data rule: no shared DB access, coordination via events/REST
only).

**Decision: Option C - new `dispute-service`** (proposed port 9012).

This is the one track of the three where ADR-004's Domain Orchestration criteria are squarely met:
"multiple aggregates are involved in a single workflow," "saga-like processes are required,"
"compensation logic may exist," "event-driven coordination is required" - the same criteria that
already justify order-service, billing-service, and payment-service being Domain Orchestration mode.
dispute-service is structurally the same pattern as order-service's saga: it owns the workflow state
(Dispute) but never owns Invoice or Payment data directly; it coordinates billing-service and
payment-service via events, and each of those services keeps sole write ownership of its own
aggregate.

## 3. Architecture mode (ADR-004)

**Domain Orchestration.** See reasoning above. dispute-service is audit-mandated (financial impact),
consistent with the platform's existing audit-mandated list (identity, customer, subscription,
payment).

## 4. State machine

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

`OPENED` requires at least one of `invoiceId` (billing-service) or `paymentId` (payment-service) to be
set, and MAY reference both (e.g. a paid invoice being disputed after settlement).

An SLA-expiry state was considered (`EXPIRED`) and rejected in favor of **reusing ticket-service's
existing SLA machinery** (Ticket/TicketComment/SLA aggregates, `ticket.sla-breached.v1`) instead of
dispute-service reinventing SLA tracking (reuse-before-build). See Section 6.

## 5. Provisional credit without corrupting billing-service's system of record

The critical design constraint: a dispute under review must not silently mutate billing-service's
financial ledger, since billing-service is the system of record for invoiced amounts.

- On entering `UNDER_REVIEW`, dispute-service publishes `dispute.opened.v1`
  `{ disputeId, invoiceId, paymentId, customerId, disputedAmount, reasonCode, openedAt }`.
- billing-service (new inbox consumer, a billing-service-owned feature, scoped in 22.4) sets
  `Invoice.disputeStatus = ON_HOLD`. This is a **hold flag**, not a financial adjustment: the invoice
  total is untouched, and the invoice is excluded from the overdue/dunning bill-run check while
  `ON_HOLD` (`invoice.overdue.v1` suppressed). This is the "provisional credit" - provisional in the
  sense of pausing collections, not creating money.
- payment-service (new inbox consumer, scoped in 22.5) marks the related Payment/PaymentAttempt with a
  disputed flag, suppressing auto-retry/auto-settlement while under review.
- On `RESOLVED_CUSTOMER`, dispute-service publishes `dispute.resolved-customer.v1`
  `{ disputeId, resolutionAmount, resolvedAt }`:
  - If the invoice is unpaid, billing-service applies a **real** adjustment: a new negative
    `InvoiceLine` of type `ADJUSTMENT`/`CREDIT` for `resolutionAmount`, clearing `disputeStatus`.
  - If the invoice/payment was already paid, payment-service instead issues a **real** refund via its
    existing `POST /api/v1/payments/{id}/refund` capability, publishing the existing
    `payment.refunded.v1` event - Track 2 reuses payment-service's refund machinery rather than
    building a parallel one.
- On `RESOLVED_MERCHANT`, dispute-service publishes `dispute.resolved-merchant.v1`; billing-service
  clears `disputeStatus` back to normal (hold lifted, invoice resumes normal dunning) with no
  financial change.
- `CLOSED` is reached once the resolution's downstream action (credit/refund/no-op) is confirmed, or
  on `WITHDRAWN`.

All cross-service coordination is event-driven (outbox/inbox), never direct DB access - satisfying
ADR-006's cross-service data rule.

## 6. Reuse: ticket-service for SLA and agent workflow

Rather than dispute-service building its own SLA-timer/assignment machinery, ticket-service (already
owning Ticket/TicketComment/SLA and `ticket.sla-breached.v1`) consumes `dispute.opened.v1` and
auto-opens a linked ticket (category `DISPUTE`) for agent review. This reuses an existing platform
capability end-to-end instead of duplicating SLA tracking - consistent with the platform's
reuse-before-build principle.

## 7. Aggregate shapes

```text
Dispute (dispute-service, dispute-db)
  id, invoiceId (nullable), paymentId (nullable), customerId
  status         : OPENED | UNDER_REVIEW | EVIDENCE_SUBMITTED | RESOLVED_CUSTOMER
                   | RESOLVED_MERCHANT | WITHDRAWN | CLOSED
  reasonCode, disputedAmount, resolutionAmount (nullable)
  openedAt, resolvedAt (nullable), closedAt (nullable)

DisputeEvidence
  id, disputeId, submittedBy, objectRef (MinIO key, mirrors customer-service's KYC-document
  object-storage pattern per ADR-006), submittedAt

DisputeStateHistory
  id, disputeId, fromStatus, toStatus, changedBy, changedAt, note
```

Billing-service and payment-service each get a small, service-owned schema addition (their own
migrations, their own write ownership): `Invoice.disputeStatus` + a `CREDIT`/`ADJUSTMENT` InvoiceLine
type in billing-service; a disputed flag on Payment/PaymentAttempt in payment-service. dispute-service
never writes to `billing-db` or `payment-db` directly.

## 8. Events (new; dispute-service is the producer)

`dispute.opened.v1`, `dispute.evidence-submitted.v1`, `dispute.resolved-customer.v1`,
`dispute.resolved-merchant.v1`, `dispute.withdrawn.v1`, `dispute.closed.v1`.

Consumers: billing-service (hold/credit), payment-service (hold/refund), ticket-service (auto-open
linked ticket), notification-service (customer communication).
