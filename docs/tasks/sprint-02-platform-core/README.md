# Sprint 02 - Platform Core Libraries

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 6/6 | 2026-06-22 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Build the framework-agnostic internal platform (`platform-core/*`): the response/exception/context
primitives, the CQRS contracts, the in-process Mediator with its pipeline behaviors, and the
outbox/inbox cores. These modules MUST NOT import Spring (PLATFORM-SPEC hard rules). Starters
(Sprint 03) wrap them for Spring Boot.

## Included Epics

- Epic 2: Platform Core Libraries (`platform-common`, `platform-cqrs`, `platform-mediator`,
  `platform-outbox`, `platform-inbox`)

## Cross-cutting constraints (apply to all tasks here)

- `platform-core/*` may import only: JDK, slf4j-api, jakarta.validation-api (optional), jakarta
  annotations, Jackson annotations. No Spring.
- GroupId `com.telco.platform`, version `1.0.0-SNAPSHOT`. Base packages per PLATFORM-SPEC Section 1.
- Records for immutable value types; every public type gets a short Javadoc.

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 2.1 | platform-common | DONE | [2.1-platform-common.md](2.1-platform-common.md) |
| 2.2 | platform-cqrs | DONE | [2.2-platform-cqrs.md](2.2-platform-cqrs.md) |
| 2.3 | platform-mediator | DONE | [2.3-platform-mediator.md](2.3-platform-mediator.md) |
| 2.4 | platform-outbox | DONE | [2.4-platform-outbox.md](2.4-platform-outbox.md) |
| 2.5 | platform-inbox | DONE | [2.5-platform-inbox.md](2.5-platform-inbox.md) |
| 2.6 | Build and Verification | DONE | [2.6-build-and-verification.md](2.6-build-and-verification.md) |

## Sprint Deliverables

- `platform-common` (API contract, exceptions, context), `platform-cqrs`, `platform-mediator`
  (dispatcher + behaviors), `platform-outbox` core, `platform-inbox` core.
- Unit-tested, Spring-free, building as a reactor.

## Exit Criteria

- Platform-core reactor builds and tests pass.
- No Spring imports anywhere in `platform-core/*`.
- Mediator ordering/short-circuit, outbox record building, and inbox first-seen logic are covered by
  passing unit tests.
</content>
