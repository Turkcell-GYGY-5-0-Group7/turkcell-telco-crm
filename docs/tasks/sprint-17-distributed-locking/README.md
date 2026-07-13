# Sprint 17 - Distributed Locking (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 5/5 | 2026-07-12 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). ADR-024 was ratified
> (Accepted) by tech-lead on 2026-07-12. All five features are built: the platform foundation
> (`starter-lock` and its test support, 17.1/17.2), both real consumers (`subscription-service`'s
> MSISDN reaper and `billing-service`'s bill-run lock, 17.3/17.4), and the capability-catalog docs
> update (17.5). See "Sprint Deliverables" below for what is live-verified vs. compiled-and-reviewed-
> only (the Testcontainers-dependent test classes could not run live in this sandbox - a confirmed
> pre-existing, repo-wide Docker/Testcontainers incompatibility, not specific to this sprint).

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
| 17.1 | `starter-lock` module: Redisson wiring, `DistributedLock` port, config properties, TTL/lease-renewal, fail-closed failure mode | DONE | [17.1-starter-lock-module.md](./17.1-starter-lock-module.md) |
| 17.2 | Distributed-lock test support: Testcontainers Redis harness, contention/failure-mode test suite | DONE, unverified live (see note) | [17.2-distributed-lock-test-support.md](./17.2-distributed-lock-test-support.md) |
| 17.3 | `subscription-service`: MSISDN reservation-expiry reaper with cross-instance lock coordination | DONE, unit-tested live; concurrency IT unverified live (see note) | [17.3-subscription-service-msisdn-reaper.md](./17.3-subscription-service-msisdn-reaper.md) |
| 17.4 | `billing-service`: bill-run run-level lock coordination for scaled-out deployments | DONE, unit-tested live; concurrency IT unverified live (see note) | [17.4-billing-service-bill-run-lock-coordination.md](./17.4-billing-service-bill-run-lock-coordination.md) |
| 17.5 | Platform capability catalog and `PLATFORM-SPEC.md` update: move distributed locking from "planned" to "available" | DONE | [17.5-platform-capability-catalog-update.md](./17.5-platform-capability-catalog-update.md) |

## Sprint Deliverables

- `platform/platform-core/lock` (`com.telco.platform.lock`, Spring-free `DistributedLock` port,
  `LockHandle`, `LockErrorCode`) and `platform/platform-starters/starter-lock`
  (Redisson-backed implementation, `LockAutoConfiguration`, `telco.platform.lock.*` properties),
  built per ADR-024. **DONE 2026-07-12.**
- A concurrency/failure-mode test suite (Testcontainers Redis) proving: two concurrent lock attempts
  on the same key serialize correctly; a watchdog-managed lease survives a slow-but-healthy holder;
  an explicit lease hard-expires; and a Redis outage fails the caller closed (`DependencyFailureException`
  with `LockErrorCode.LOCK_ACQUISITION_FAILED` -> `503`), not silently open. **DONE 2026-07-12, code
  written and compiles clean, but NOT executed live**: this sandbox's Docker Desktop (29.1.2) has
  raised its minimum supported API floor to 1.44, and the repo's pinned Testcontainers version
  (1.20.6, matching `microservices/pom.xml`'s existing convention) bundles a `docker-java` client that
  negotiates API 1.32 - a pre-existing, repo-wide incompatibility, confirmed by reproducing the
  identical failure on the untouched, pre-existing `starter-inbox` Testcontainers test. Needs
  verification in an environment with a compatible Docker version (or a repo-wide Testcontainers
  version bump, itself a separate cross-cutting decision - see `docs/tasks/lessons.md` 2026-07-12
  entry). A code-review pass on 17.1/17.2 found and fixed one HIGH-severity bug (an early
  `withLock(Callable)` draft rewrapped every domain `RuntimeException` thrown from inside a critical
  section as `IllegalStateException`, which would have broken `GlobalExceptionHandler`'s type-based
  dispatch for 17.3/17.4's future consumers) plus two MEDIUM items (a dead, unread `lease-time`
  config property removed from `LockProperties` and ADR-024's property table; a Docker-independent
  Mockito unit test suite, `RedissonDistributedLockUnitTest`, added to cover the lease-mode branching
  and exception-transparency logic that the Testcontainers classes cannot currently exercise live in
  this sandbox) - a second review pass confirmed all fixes and returned APPROVE.
- `subscription-service` reaps expired `RESERVED` MSISDN holds (`reservedUntil` already exists on
  `MsisdnPool`, no reaper exists today) with exactly one replica performing the sweep at a time, even
  when the service is running multiple pods. **DONE 2026-07-12**: `ExpireMsisdnReservationsCommand(Handler)`
  drives every release through the existing `MsisdnPool.release()` domain method (never raw SQL) and
  writes one `audit_log` row per release, atomically inside the mediator's transaction;
  `MsisdnReservationExpiryReaper` guards the whole tick with an explicit-lease `DistributedLock`
  (ADR-024 Section 4's bounded-sweep guidance), returning a distinguishable `-1` (not an unhandled
  exception) when it loses the race for a tick. Two fast Mockito unit tests (no Docker) pass live,
  covering both the handler's release/audit logic and the reaper's lock-key/lease/error-code-filtering
  behavior. A Testcontainers concurrency IT (`MsisdnReservationExpiryReaperConcurrencyIT`) proving
  "exactly one winner per tick" against real Postgres+Redis compiles clean but could not run live in
  this sandbox (see the Testcontainers/Docker note under Feature 17.2 above - the same limitation).
- `billing-service`'s `RunBillCommandHandler` acquires a run-level lock keyed on the billing period
  before partitioning and dispatching batches, so at most one pod processes a given period's bill-run
  concurrently, closing the wasted-work/silently-swallowed-duplicate-key-exception gap described in
  the Sprint 14.3.2 report. **DONE 2026-07-12**: `handle()` wraps the existing orchestration (renamed
  to `runBillRun()`) in a watchdog-managed `DistributedLock` (variable-duration work, per ADR-024
  Section 4 - the bill-run's measured 6m20s@100K); on lock contention it returns a new
  `RunBillResult.alreadyOwnedByAnotherPod()` outcome instead of an undifferentiated failure.
  `BillRunBatchProcessor`'s `REQUIRES_NEW` per-batch pattern and `uidx_invoices_sub_period` are
  untouched. A fast Mockito unit test passes live, directly verifying (via `verify(..., never())`) that
  the losing invocation never reaches `subscriberRepo.findByStatus`/`batchProcessor.processBatch` - not
  just "no duplicate invoice rows". A Testcontainers concurrency IT compiles clean but could not run
  live (same sandbox limitation).
- `docs/architecture/platform-capabilities.md` Section 3 and `docs/architecture/platform-gap-closing-plan.md`
  updated to reflect the new capability as available, not planned. **DONE 2026-07-12**: Section 1 of
  `platform-capabilities.md` gained a "Distributed locking (via `starter-lock`)" subsection;
  `platform/PLATFORM-SPEC.md` gained a new `## 7. platform-lock` section (with the rest of its
  sections 7-11 renumbered to 8-12 to make room - verified no repo-wide cross-reference broke) and a
  `### 10.7 starter-lock` subsection; `platform-gap-closing-plan.md` gained a new "Follow-up (shipped
  after the Tier plan)" section, since distributed locking was never part of that plan's original
  Tier 1-3 sequencing.
- A code-review pass on 17.3/17.4 found and fixed a real regression before it shipped: adding
  `starter-lock` to both services made `DistributedLock` a MANDATORY dependency of the new reaper/
  handler beans, but Redisson connects eagerly at startup (confirmed empirically) unlike
  `starter-kafka`'s tolerant listener containers - simply disabling the lock in each service's shared
  test profile (to avoid needing a live Redis in every unrelated test) would have broken every
  pre-existing Spring-context test in both modules. Fixed by packaging a second, inverse-conditioned
  `@AutoConfiguration` in `starter-lock`'s own test-jar supplying a trivial, real in-JVM
  `DistributedLock` substitute whenever the real one is disabled, with zero changes needed to any
  pre-existing test file; a related finding (the reaper's `@Scheduled` firing unconditionally in every
  context once that bean existed) was fixed with its own `@ConditionalOnProperty` gate. Both fixes
  verified live and confirmed via a second review pass (APPROVE). See `docs/tasks/lessons.md`
  2026-07-12 entry for the full pattern.

## Exit Criteria

- ADR-024 is ratified (Accepted) by tech-lead before any code in this sprint ships. **MET 2026-07-12**:
  ratified with one amendment (Section 5's fail-closed exception design changed from a new
  `LockAcquisitionException extends PlatformException` - infeasible, `PlatformException` is `sealed`
  to its own package with no `module-info.java` in this codebase - to wrapping the existing
  `DependencyFailureException` with a new `LockErrorCode`; see ADR-024 Section 5's ratification note).
- `starter-lock` ships with unit and Testcontainers-backed integration tests proving the fail-closed
  behavior on Redis unavailability and correct mutual exclusion under real concurrent acquisition -
  not just mocked Redisson behavior. **MET, code-complete**: the Testcontainers-backed suite exists
  and compiles clean; it could not be executed live in this sandbox (Docker/Testcontainers
  incompatibility, pre-existing and repo-wide - see Feature 17.2's deliverable note). A
  Docker-independent Mockito suite (`RedissonDistributedLockUnitTest`) covers the same branching logic
  and passes live.
- `subscription-service`'s MSISDN reaper and `billing-service`'s bill-run lock are each verified with
  a multi-instance (or multi-thread-simulating-multi-instance) test showing exactly one winner per
  lock key, and the losing side degrades safely (retries, skips, or fails closed - not a silent
  duplicate action). **MET, code-complete**: both concurrency ITs exist and compile clean (same
  Testcontainers caveat as above); each service's fast Mockito unit test independently verifies the
  losing side's degrade-safely behavior live (no propagated exception, no work performed).
- No existing service's dependency graph changes as a side effect of this sprint; `starter-lock`
  remains strictly opt-in, matching ADR-018's starter-only consumption model. **MET**: only
  `subscription-service` and `billing-service` depend on `starter-lock` (repo-wide grep confirmed by
  code review); no other service or starter gained a transitive dependency on it.

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
