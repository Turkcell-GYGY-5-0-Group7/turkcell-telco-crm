# Sprint 004 - Onboarding Saga

| Field | Value |
| --- | --- |
| Sprint | 004 |
| Epic | EPIC-006 Onboarding Saga |
| Phase | P2 |
| Status | TODO |
| Progress | 0/5 |
| Started | - |
| Completed | - |

## Goal

Deliver end-to-end new-line activation (Phase P2, AC-01).

## Tasks

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| T-018 | order-service: order intake, saga orchestration, SagaState persistence (FR-09..11) | TODO | Domain Orchestration mode |
| T-019 | order-service: cancellation and compensation events (FR-12) | TODO | Compensation flow |
| T-020 | payment-service: mock PSP, idempotency, retry at 24/72/168h (FR-25..27) | TODO | Domain Orchestration; uses starter-inbox |
| T-021 | subscription-service: activation, MSISDN allocation, lifecycle transitions (FR-13..15) | TODO | CQRS + Mediator |
| T-022 | End-to-end test for AC-01 including failure compensation | TODO | QA |

## Definition of Done

- AC-01 (New Subscriber Onboarding) passes end to end, including refund/cancel on failure.
- Saga events flow via the outbox; consumers are idempotent.

## Dependencies

- Sprint 003 (customer, catalog, identity) and BL-01 infra.

## Agent Assignments

- Microservice Generator Agent -> service scaffolding
- Event Integration Agent -> saga events and outbox
- QA Agent -> AC-01 end-to-end test
- Architecture Agent -> Domain Orchestration validation
- Tech Lead Agent -> final approval
