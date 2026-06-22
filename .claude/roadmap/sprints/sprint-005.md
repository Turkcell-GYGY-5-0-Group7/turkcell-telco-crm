# Sprint 005 - Revenue Cycle

| Field | Value |
| --- | --- |
| Sprint | 005 |
| Epic | EPIC-007 Revenue Cycle |
| Phase | P3 |
| Status | TODO |
| Progress | 0/5 |
| Started | - |
| Completed | - |

## Goal

Deliver usage-driven billing (Phase P3, AC-02 and AC-03).

## Tasks

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| T-023 | usage-service: CDR consumption and quota updates (FR-17, FR-18) | TODO | CQRS + Mediator; Kafka consumer |
| T-024 | usage-service: 80%/100% threshold events and overage aggregation (FR-19, FR-20) | TODO | Publishes quota events |
| T-025 | billing-service: monthly bill-run scheduler and invoice generation (FR-21, FR-22) | TODO | Domain Orchestration |
| T-026 | billing-service: PDF rendering and InvoiceGenerated/InvoicePaid events (FR-23, FR-24) | TODO | Object storage for PDF |
| T-027 | CDR simulator and end-to-end tests for AC-02 and AC-03 | TODO | QA |

## Definition of Done

- AC-02 (Monthly Billing) and AC-03 (Quota Exhaustion) pass end to end.
- Bill-run performance target tracked toward NFR-02.

## Dependencies

- Sprint 004 (active subscriptions) and BL-01 infra.

## Agent Assignments

- Microservice Generator Agent -> service scaffolding
- Event Integration Agent -> CDR and billing events
- QA Agent -> AC-02 and AC-03 end-to-end tests
- Architecture Agent -> mode validation
- Tech Lead Agent -> final approval
