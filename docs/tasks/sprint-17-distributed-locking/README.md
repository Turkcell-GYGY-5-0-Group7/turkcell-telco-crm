# Sprint 17 - Distributed Locking (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-07-11 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). It is documented now
> and built later (ADR-024). Feature subtask files will be authored when the sprint is scheduled.

## Objective

Add a Redis-backed distributed-locking capability to the platform (ADR-024) and adopt it in the two
real internal call sites that motivated it: `subscription-service`'s MSISDN pool (a
reservation-expiry reaper that must not race across replicas once the service scales, FR-13) and
`billing-service`'s bill-run orchestration (run-level coordination so two pods cannot both own the
same billing period in a scaled-out deployment, NFR-02,
`docs/tasks/sprint-14-testing-and-hardening/14.3.2-bill-run-throughput-report.md`). Ships as a new
optional starter (`starter-lock`) per ADR-018's starter-only consumption model - no existing service
is required to adopt it, and no existing platform module is modified to add it (ADR-007's
new-module Extension Rule).

## Included Epics

- Epic 17: Distributed Coordination (Redis-backed distributed locking)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 17.1 | `starter-lock` module: Redisson wiring, `DistributedLock` port, config properties, TTL/lease-renewal, fail-closed failure mode | TODO | [17.1-starter-lock-module.md](./17.1-starter-lock-module.md) |
| 17.2 | Distributed-lock test support: Testcontainers Redis harness, contention/failure-mode test suite | TODO | [17.2-distributed-lock-test-support.md](./17.2-distributed-lock-test-support.md) |
| 17.3 | `subscription-service`: MSISDN reservation-expiry reaper with cross-instance lock coordination | TODO | [17.3-subscription-service-msisdn-reaper.md](./17.3-subscription-service-msisdn-reaper.md) |
| 17.4 | `billing-service`: bill-run run-level lock coordination for scaled-out deployments | TODO | [17.4-billing-service-bill-run-lock-coordination.md](./17.4-billing-service-bill-run-lock-coordination.md) |
| 17.5 | Platform capability catalog and `PLATFORM-SPEC.md` update: move distributed locking from "planned" to "available" | TODO | [17.5-platform-capability-catalog-update.md](./17.5-platform-capability-catalog-update.md) |

## Sprint Deliverables

- `platform/platform-core/lock` (`com.telco.platform.lock`, Spring-free `DistributedLock` port,
  `LockHandle`, `LockAcquisitionException`) and `platform/platform-starters/starter-lock`
  (Redisson-backed implementation, `LockAutoConfiguration`, `telco.platform.lock.*` properties),
  built per ADR-024.
- A concurrency/failure-mode test suite (Testcontainers Redis) proving: two concurrent lock attempts
  on the same key serialize correctly; a watchdog-managed lease survives a slow-but-healthy holder;
  an explicit lease hard-expires; and a Redis outage fails the caller closed
  (`LockAcquisitionException` -> `503`), not silently open.
- `subscription-service` reaps expired `RESERVED` MSISDN holds (`reservedUntil` already exists on
  `MsisdnPool`, no reaper exists today) with exactly one replica performing the sweep at a time,
  even when the service is running multiple pods.
- `billing-service`'s `RunBillCommandHandler` acquires a run-level lock keyed on the billing period
  before partitioning and dispatching batches, so at most one pod processes a given period's bill-run
  concurrently, closing the wasted-work/silently-swallowed-duplicate-key-exception gap described in
  the Sprint 14.3.2 report.
- `docs/architecture/platform-capabilities.md` Section 3 and `docs/architecture/platform-gap-closing-plan.md`
  updated to reflect the new capability as available, not planned.

## Exit Criteria

- ADR-024 is ratified (Accepted) by tech-lead before any code in this sprint ships (the ADR is
  Proposed as of this drafting).
- `starter-lock` ships with unit and Testcontainers-backed integration tests proving the fail-closed
  behavior on Redis unavailability and correct mutual exclusion under real concurrent acquisition -
  not just mocked Redisson behavior.
- `subscription-service`'s MSISDN reaper and `billing-service`'s bill-run lock are each verified with
  a multi-instance (or multi-thread-simulating-multi-instance) test showing exactly one winner per
  lock key, and the losing side degrades safely (retries, skips, or fails closed - not a silent
  duplicate action).
- No existing service's dependency graph changes as a side effect of this sprint; `starter-lock`
  remains strictly opt-in, matching ADR-018's starter-only consumption model.

## References

- [ADR-024 Distributed Lock Strategy](../../../architecture/adr/ADR-024-distributed-lock-strategy.md)
- [ADR-007 Platform Library Strategy](../../../architecture/adr/ADR-007-platform-library-strategy.md) -
  new-module extension rule this sprint follows.
- [ADR-018 Platform Starter Dependency Model](../../../architecture/adr/ADR-018-platform-starter-dependency-model.md) -
  starter-only consumption rule `starter-lock` follows.
- [docs/architecture/platform-capabilities.md](../../architecture/platform-capabilities.md) Section 3
  ("Planned - not yet available") - this sprint closes one of these gaps end to end.
- [docs/architecture/platform-gap-closing-plan.md](../../architecture/platform-gap-closing-plan.md) -
  the existing tiered gap-closing sequencing this capability slots into as a follow-up entry.
- [docs/architecture/service-catalog.md](../../architecture/service-catalog.md) Section 5
  (Infrastructure Profile) - existing Redis consumers (`api-gateway` rate limiter,
  `product-catalog-service` cache-aside, `payment-service` idempotency keys) this sprint's Redisson
  client sits alongside.
- [docs/architecture/security-posture.md](../../architecture/security-posture.md) Section 6 - the
  gateway rate limiter's fail-OPEN-on-Redis-outage precedent that ADR-024 deliberately diverges from
  for locking (fail CLOSED).
- `docs/tasks/sprint-14-testing-and-hardening/14.3.2-bill-run-throughput-report.md` - the bill-run
  batching design (`RunBillCommandHandler`, `BillRunBatchProcessor`) Feature 17.4 adds lock-based
  coordination on top of, without changing.
- `microservices/subscription-service/src/main/java/com/telco/subscription/domain/MsisdnPool.java`
  and `.../infrastructure/MsisdnPoolRepository.java` - the existing state machine and
  `FOR UPDATE SKIP LOCKED` allocation path Feature 17.3's reaper complements (does not replace).
