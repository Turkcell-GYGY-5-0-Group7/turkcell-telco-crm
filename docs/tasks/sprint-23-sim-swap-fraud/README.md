# Sprint 23 - SIM-Swap / Fraud Detection (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-07-11 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. It is documented now (design pass + Proposed ADR) and built later.
> Feature subtask files will be authored when the sprint is scheduled.

## Objective

Deliver a pragmatic, MVP-appropriate SIM-swap / fraud detection capability: a new `fraud-service`
(CQRS + Mediator per ADR-004) that reacts to existing Kafka domain events
(`msisdn.allocated.v1`/`msisdn.released.v1`/`subscription.*`) with rule-based velocity checks, rather
than the streaming/lakehouse/ML version proposed in TELCO-CRM-ADVANCED.md Section 4.4, which is
explicitly deferred. Built per ADR-029 (new service, database-per-service, detect-and-alert-only
response model).

## Included Epics

- Epic 23: SIM-Swap and Fraud Detection (MVP scope)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 23.1 | fraud-service scaffold and schema (ADR-017 template, `fraud-db`, FraudCase/FraudSignal/FraudRule/MsisdnLifecycleSignal) | TODO | [23.1-fraud-service-scaffold-and-schema.md](23.1-fraud-service-scaffold-and-schema.md) |
| 23.2 | Rule evaluation: RAPID_SIM_SWAP, MSISDN_CHURN_VELOCITY (consume msisdn/subscription events via inbox) | TODO | [23.2-rule-evaluation-engine.md](23.2-rule-evaluation-engine.md) |
| 23.3 | Fraud case API (list/view/resolve case, admin rule-threshold config) | TODO | [23.3-fraud-case-api.md](23.3-fraud-case-api.md) |
| 23.4 | Fraud eventing (`fraud.signal-raised.v1`, `fraud.case-opened.v1`) + ticket-service auto-ticket consumer | TODO | [23.4-fraud-eventing-and-ticket-integration.md](23.4-fraud-eventing-and-ticket-integration.md) |
| 23.5 | Tests (unit/integration; rolling-window velocity scenarios) | TODO | [23.5-tests.md](23.5-tests.md) |

## Sprint Deliverables

- `fraud-service` (new, port 9013 proposed), CQRS + Mediator mode, with its own `fraud-db`.
- Rule-based detection of rapid MSISDN release/reallocation and unusual allocate/release velocity,
  consuming events subscription-service already publishes.
- `fraud.case-opened.v1` auto-opens a linked ticket in ticket-service for agent review; no automated
  subscription suspension in this phase.

## Exit Criteria

- A simulated rapid MSISDN release-then-reallocate for the same customer raises a `RAPID_SIM_SWAP`
  signal and, on repetition/severity, opens a `FraudCase` and an auto-linked ticket.
- fraud-service consumes only existing published events (no new subscription-service events required)
  and never accesses `subscription-db` directly (ADR-006 verified).
- No automated account suspension occurs without agent action, per the deliberate MVP scope-down in
  ADR-029.

## References

- [ADR-029 Fraud Detection MVP Scope](../../../architecture/adr/ADR-029-fraud-detection-mvp-scope.md)
- [design-note.md](design-note.md)
- [service-catalog.md](../../architecture/service-catalog.md)
- [event-catalog.md](../../architecture/event-catalog.md)
