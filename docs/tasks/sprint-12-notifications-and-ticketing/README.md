# Sprint 12 - Notifications and Ticketing

## Objective

Build notification-service (9009) and ticket-service (9010): multi-channel templated notifications
that consume domain events and respect user preferences, and customer ticketing with SLA-based
auto-assignment. Notification-service closes the loops opened by earlier sprints (welcome SMS,
invoice email, quota-threshold SMS).

Covers FR-28, FR-29, FR-30 (notification) and FR-31, FR-32, FR-33 (ticket).

## Included Epics

- Epic 12: Engagement and Support (notification-service, ticket-service)

## Features (one file per top-level task)

| ID | Feature | File |
| --- | --- | --- |
| 12.1 | Notification Service - Scaffold and Schema | [12.1-notification-service-scaffold-and-schema.md](12.1-notification-service-scaffold-and-schema.md) |
| 12.2 | Notification Channels and Templates | [12.2-notification-channels-and-templates.md](12.2-notification-channels-and-templates.md) |
| 12.3 | Notification Eventing and API | [12.3-notification-eventing-and-api.md](12.3-notification-eventing-and-api.md) |
| 12.4 | Ticket Service - Scaffold and Schema | [12.4-ticket-service-scaffold-and-schema.md](12.4-ticket-service-scaffold-and-schema.md) |
| 12.5 | Ticket Domain and API | [12.5-ticket-domain-and-api.md](12.5-ticket-domain-and-api.md) |
| 12.6 | Tests | [12.6-tests.md](12.6-tests.md) |

## Sprint Deliverables

- notification-service (9009): mock SMS/email/push channels, templates, preference enforcement,
  domain-event consumers closing AC-01/02/03 messaging, and notification API.
- ticket-service (9010): SLA-based ticket open/assign/resolve, comments, SLA-breach detection, and
  ticket events.
- Integration tests for both.

## Exit Criteria

- Domain events trigger preference-respecting templated notifications; the AC-01 welcome SMS, AC-02
  invoice email, and AC-03 quota SMS dispatch through notification-service.
- Tickets are SLA-auto-assigned on open, notify the customer, and progress through resolve with
  breach detection.
- FR-28, FR-29, FR-30, FR-31, FR-32, FR-33 pass.
</content>
