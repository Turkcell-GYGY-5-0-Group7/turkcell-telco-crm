# ticket-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9010 |
| Mode | CQRS + Mediator |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 12](../tasks/sprint-12-notifications-and-ticketing/README.md) |
| Build status | TODO |
| Requirements | FR-31, FR-32, FR-33 |

Bounded context: customer requests/complaints with SLA-based auto-assignment and breach detection.

## Authentication and Authorization

All endpoints require a valid JWT. Assignment and resolution require an agent role.

## Endpoints

| Method | Path | Auth | Idempotency | Summary |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/tickets` | JWT | optional | Open a ticket; SLA auto-assigned. |
| GET | `/api/v1/tickets/{id}` | JWT | - | Fetch a ticket with comments. |
| GET | `/api/v1/tickets` | JWT | - | List/search tickets (paged). |
| POST | `/api/v1/tickets/{id}/comments` | JWT | - | Add a comment. |
| POST | `/api/v1/tickets/{id}/assign` | RBAC agent | - | Assign to a team/agent. |
| POST | `/api/v1/tickets/{id}/resolve` | RBAC agent | - | Resolve the ticket. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `ticket.opened.v1`, `ticket.assigned.v1`, `ticket.resolved.v1`, `ticket.sla-breached.v1` |
| Consume | `invoice.overdue.v1` (may open a follow-up ticket) |

## Notes

- Tickets are SLA-auto-assigned on open and notify the customer.
- SLA-breach detection emits `ticket.sla-breached.v1` for escalation.

Reference: [service-catalog](../architecture/service-catalog.md), [event-catalog](../architecture/event-catalog.md), ADR-015.
