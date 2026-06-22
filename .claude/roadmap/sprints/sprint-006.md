# Sprint 006 - Engagement and Support

| Field | Value |
| --- | --- |
| Sprint | 006 |
| Epic | EPIC-008 Engagement and Support |
| Phase | P4 |
| Status | TODO |
| Progress | 0/5 |
| Started | - |
| Completed | - |

## Goal

Deliver customer communication and support (Phase P4).

## Tasks

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| T-028 | notification-service: SMS/email/push adapters (mock channels) (FR-28) | TODO | Simple Service Layer mode |
| T-029 | notification-service: template management and event-to-template mapping (FR-29) | TODO | Consumes most domain events |
| T-030 | notification-service: opt-in/opt-out preference handling (FR-30) | TODO | Respect communication preferences |
| T-031 | ticket-service: ticket creation, comments, SLA-based assignment (FR-31, FR-32) | TODO | CQRS + Mediator |
| T-032 | ticket-service: notification on ticket open and SLA breach (FR-33) | TODO | Emits ticket events |

## Definition of Done

- Domain events trigger templated notifications respecting preferences.
- SLA-based ticket assignment works; ticket events flow to notification.

## Dependencies

- Sprints 003-005 (events to react to) and BL-01 infra.

## Agent Assignments

- Microservice Generator Agent -> service scaffolding
- Event Integration Agent -> notification consumers
- Architecture Agent -> mode validation (Simple / CQRS + Mediator)
- Tech Lead Agent -> final approval
