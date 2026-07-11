# ADR-024 Distributed Lock Strategy

Status: Proposed
Date: 2026-07-11

## Context

Redis is already a first-class platform dependency: `api-gateway` uses it for the rate limiter,
`product-catalog-service` uses it for cache-aside, and `payment-service` uses it for idempotency
keys (`docs/architecture/service-catalog.md` Section 5, Infrastructure Profile). What the platform
does not yet provide is a **distributed mutual-exclusion primitive** - a way for a critical section
to be coordinated across JVM instances (multiple pods of the same service, or two different
services), as opposed to within a single JVM (`synchronized`, a `ReentrantLock`) or within a single
Postgres transaction (`SELECT ... FOR UPDATE`).

A repo-wide search confirms this is genuinely missing today: no Redisson dependency, no `RLock`
usage, no `starter-lock` module anywhere in `platform/`. It is also not mentioned in
`docs/product/TELCO-CRM-ADVANCED.md`, so this is new scope, not a pull-forward of already-designed
work, and is not yet reflected in `docs/architecture/platform-gap-closing-plan.md`'s tier table or
`platform-capabilities.md` Section 3's "planned" list - both of which this ADR's ratification should
feed into as a follow-up, not as part of this document.

Two real, already-shipped code paths motivate this now:

* **`subscription-service` MSISDN pool allocation** (`domain/MsisdnPool.java`,
  `infrastructure/MsisdnPoolRepository.java`, FR-13). The FREE -> RESERVED -> ALLOCATED transition
  inside a single command is already race-safe: `findNextFreeForUpdate()` uses
  `SELECT ... FOR UPDATE SKIP LOCKED`, a row-level Postgres lock that is correct across concurrently
  running pods because it lives in the database, not in a JVM. The actual gap is at the edges of
  that state machine, which a single-row DB lock cannot reach: `reservedUntil` exists on the entity
  but no reaper currently exists to expire stale RESERVED holds back to FREE, and once one is built
  (a natural next feature) it will run as a `@Scheduled` task on every replica once
  subscription-service scales out (Sprint 15 HPA) - without cross-instance coordination, every pod
  would race to reap the same rows on every tick. Bulk pool-replenishment/admin operations have the
  same shape. This ADR scopes the *mechanism*; the reaper itself is a subscription-service feature
  (tracked in Sprint 17, Feature 17.3).
* **`billing-service` bill-run orchestration** (`RunBillCommandHandler`,
  `BillRunBatchProcessor`, `docs/tasks/STATUS.md` Sprint 14.3.2). Today, one `RunBillCommand`
  invocation partitions active subscribers into batches and runs them concurrently *inside a single
  JVM* via a local `ExecutorService`; each batch commits independently
  (`@Transactional(propagation = REQUIRES_NEW)`). The unique index
  `uidx_invoices_sub_period` and the per-subscriber existence check make actual duplicate invoice
  *rows* impossible, but in a scaled-out deployment nothing stops two billing-service pods from
  both being triggered for the same billing period concurrently (a retried scheduler/admin call, or
  a cron hitting more than one replica) - both would do the full batch-partition and per-subscriber
  work, racing on the existence check, with the loser's writes failing on the unique constraint and
  being silently swallowed by `BillRunBatchProcessor`'s per-subscriber `catch (Exception e)`. That is
  wasted work today and a real diagnostic gap (a lost invoice attempt logs as a generic error, not a
  distinguishable "another pod already owns this run"), and only gets worse as billing-service scales
  under HPA. A single lock keyed on the bill-run period would let one pod own the run.

Both are coordination problems a database row lock cannot solve because it doesn't exist yet (the
reaper) or because the contended resource is "which pod runs this batch of work," not a specific
row. This ADR decides how the platform provides that primitive.

## Decision

### 1. Lock library: Redisson

The platform adopts **Redisson** (`org.redisson:redisson`) as the distributed-lock client.

Redis is already the platform's chosen store for exactly this class of shared, ephemeral,
cross-instance state (cache, rate limiting, idempotency keys), so building the lock primitive on the
same store is the lowest-new-infrastructure option and keeps the platform's operational surface
unchanged (no new stateful dependency to run, back up, or monitor). Redisson specifically (over
lighter Spring-native options, see Alternatives) is chosen because it implements the pieces a
correct distributed lock actually needs and the platform would otherwise have to hand-roll:
reentrant `RLock` semantics, a lease-based `SET NX PX` acquisition with a safe Lua-scripted unlock
(compare-and-delete by lock holder id, avoiding one holder releasing another's lock after a stall),
and - the key differentiator - a **watchdog** that automatically extends a lock's TTL while its
holder process is alive, so a slow but healthy operation (like a bill-run batch) does not lose its
lock mid-flight, while a crashed holder's lock still expires and self-heals. Building that
watchdog/lease-renewal behavior correctly by hand on top of raw `SET NX PX` is exactly the kind of
infrastructure risk ADR-007 tells the platform to avoid re-implementing per capability.

### 2. Module: `starter-lock`, home `platform/platform-starters/`

Per ADR-007's Extension Rule ("new platform capabilities MUST be added as a new module, not a
modification of an existing module"), this ships as a new sibling module, following the existing
`platform/platform-starters/` layout. Current siblings for reference:

```text
platform/platform-starters/
├── starter-api
├── starter-inbox
├── starter-kafka
├── starter-log-persistence
├── starter-mediator
├── starter-observability
├── starter-outbox
└── starter-security
```

`starter-lock` is added alongside these. Following the existing split between core logic and Spring
wiring (`platform-core/outbox` + `starter-outbox`, `platform-core/inbox` + `starter-inbox` per
`platform/PLATFORM-SPEC.md` Section 1's per-module package table):

* **`platform-core/lock`** (`com.telco.platform.lock`, Spring-free, matching the purity constraint
  PLATFORM-SPEC already applies to outbox/inbox/mediator behaviors - "depend on ports, not Spring"):
  the `DistributedLock` port, `LockHandle`, and `LockAcquisitionException`. No Redisson types appear
  in this module's public API - keeping the port infrastructure-agnostic and independently unit
  testable with an in-memory fake, and keeping Redisson (a third-party client) out of
  `platform-core`'s own dependency graph.
* **`starter-lock`** (`com.telco.platform.starter.lock`): the Redisson-backed
  `RedissonDistributedLock` implementation, `LockAutoConfiguration` (builds the `RedissonClient` bean
  from `telco.platform.lock.*` properties, conditional on `telco.platform.lock.enabled` and on
  Redisson being on the classpath, `@ConditionalOnMissingBean` override points per ADR-018 Section 3),
  and `LockProperties`.

Config properties, root `telco.platform.lock` (matching the existing `telco.platform.<module>.*`
convention):

| Property | Default | Meaning |
| --- | --- | --- |
| `telco.platform.lock.enabled` | `true` | Master on/off switch. |
| `telco.platform.lock.redis.address` | reuses `spring.data.redis.host`/`port` if unset | Redisson connection target; single/sentinel/cluster mode per standard Redisson config. |
| `telco.platform.lock.wait-time` | `5s` | Max time a caller blocks trying to acquire before failing. |
| `telco.platform.lock.lease-time` | unset (watchdog-managed) | Optional explicit TTL; see Section 4. |
| `telco.platform.lock.watchdog-timeout` | `30s` | Redisson's internal lock-watchdog-timeout, used when `lease-time` is unset. |

The platform BOM (`platform-bom`) pins the Redisson version; `starter-lock` depends on the plain
`org.redisson:redisson` client artifact, not Redisson's own opinionated
`redisson-spring-boot-starter`, so the platform - not a third-party starter - owns the
`AutoConfiguration` and property surface, matching how `starter-security` wraps Spring Security
rather than delegating to an unrelated starter.

### 3. API shape: an explicit `DistributedLock` port, not a pipeline behavior

```java
package com.telco.platform.lock;

public interface DistributedLock {

    LockHandle acquire(String key, Duration leaseTime); // try-with-resources: LockHandle extends AutoCloseable

    <T> T withLock(String key, Duration leaseTime, Callable<T> action);

    void withLock(String key, Duration leaseTime, Runnable action);
}
```

Services inject `DistributedLock` and call it explicitly around the critical section, the same way
`OutboxService.publish(...)` is called explicitly rather than inferred - consistent with this
platform's stated default of "prefer explicit over magic." A declarative
`@DistributedLock`-annotation-plus-pipeline-behavior model (mirroring `AuthorizationRule`/`@Audited`)
was considered and rejected for v1: pipeline behaviors wrap an entire command handler invocation
(`PipelineOrder`, `platform/PLATFORM-SPEC.md` Section 4.1), but a lock's critical section is
frequently a *sub-span* of a handler - e.g. only the reaper's expiry sweep, or only the bill-run's
per-period ownership check, not the whole transactional command - so forcing lock scope to equal
handler scope would either over-hold the lock (worse contention, longer hold under the watchdog) or
require key-derivation-from-command-fields machinery this ADR does not need to build for two known
consumers. An annotation-based mode remains a possible future addition (tracked as a
`platform-capabilities.md` follow-up) once there are enough call sites to justify it; it is not
precluded by this API shape, since it would be implemented as a pipeline behavior delegating to the
same `DistributedLock` port.

### 4. TTL, lease renewal, and liveness semantics

Two supported modes, both exposed through the same `leaseTime` parameter:

* **Watchdog-managed (recommended default): pass `null`/omit `leaseTime`.** Redisson acquires the
  lock with its internal `watchdog-timeout` (default 30s, `telco.platform.lock.watchdog-timeout`)
  and automatically renews it every third of that interval for as long as the holder's process is
  alive. This is the right choice for variable-duration work like a bill-run coordination lock: the
  lock does not expire out from under a legitimately slow but healthy holder, yet still self-heals
  (no manual renewal, no explicit release) if the holder JVM crashes or is killed, because a dead
  process stops renewing and the lock expires on its own after the watchdog interval elapses.
* **Explicit lease: pass a `Duration`.** Redisson skips the watchdog entirely; the lock hard-expires
  at exactly `leaseTime` regardless of whether the holder is still alive and working. This is for
  short, bounded critical sections (e.g. the MSISDN reaper's per-tick sweep) where the caller can
  reason about a safe upper bound and prefers a deterministic worst-case release over
  liveness-tracking.

Callers choose per call site; the platform does not force one mode. Guidance in the module Javadoc:
default to watchdog-managed unless the operation has a known, bounded duration.

### 5. Failure mode: fail CLOSED (a deliberate divergence from the gateway's rate limiter)

If Redis is unavailable when `acquire`/`withLock` is called (connection failure, timeout, or the
`wait-time` budget expires without acquiring the lock), `DistributedLock` throws
`LockAcquisitionException` and the caller's critical section **does not run**.
`LockAcquisitionException` is a `PlatformException` subtype mapped by the platform
`GlobalExceptionHandler` to `503`/`DependencyFailureException`-equivalent semantics, consistent with
how other infrastructure-unavailability failures are surfaced (`docs/architecture/platform-capabilities.md`
Section 1).

This is the opposite of the gateway rate limiter's documented behavior
(`docs/architecture/security-posture.md` Section 6: "Fails OPEN on a Redis outage... a deliberate
availability-over-strictness tradeoff") and that difference is deliberate, not an oversight. The two
are not the same risk profile:

* Rate limiting fails open into a **bounded, recoverable** harm - temporarily unmetered request
  volume - in exchange for keeping the edge available. The worst case is degraded fairness/capacity,
  not corrupted data.
* Locking exists specifically to prevent an **unbounded or irrecoverable** harm - two pods both
  allocating the same MSISDN, two pods both running (and partially committing) the same bill-run
  period. Failing open here would silently remove the exact guarantee the lock was introduced to
  provide, at precisely the moment (infrastructure instability) when uncoordinated concurrent access
  is most likely to actually happen. A degraded-but-safe "operation temporarily unavailable" (503) is
  a strictly better outcome than a degraded-and-unsafe "operation ran without coordination."

Where a future call site genuinely prefers availability over strict coordination (a low-stakes,
idempotent-enough operation), that would be an explicit, named opt-in on that call site, not a
platform-wide default - out of scope for this ADR.

### 6. Fit with ADR-018's dependency model

`starter-lock` is an **optional** starter, added the same way `starter-outbox`/`starter-inbox` are:
a service adds it only if it needs cross-instance coordination. Consuming services depend ONLY on
`starter-lock`; they MUST NOT depend on `platform-core/lock` or the `org.redisson` artifact directly
(ADR-018 Section 2's unscoped Dependency Rule applies in full here - unlike the
`platform-event-contracts` carve-out in ADR-018's 2026-07-08 amendment, `starter-lock` is exactly the
shape of module that carve-out excludes: it ships `AutoConfiguration`, builds a stateful
`RedissonClient` bean, and performs I/O, so it is fully in-scope for "must be consumed only through a
starter," not a candidate for a similar exception). No amendment to ADR-018 is needed; this is a
straightforward new instance of the existing rule.

## Consequences

### Positive

* Closes a real coordination gap with a proven client (Redisson) instead of two services hand-rolling
  incompatible `SET NX`-based locks.
* Fail-closed default protects the two known consumers' correctness guarantees (no duplicate MSISDN
  allocation, no duplicate/racing bill-run ownership) even under Redis instability.
* Watchdog-managed leases remove an entire class of "lock expired mid-operation" bugs for
  variable-duration work without requiring every caller to implement manual renewal.
* Consistent with ADR-018: one more optional starter, zero change to the dependency model.

### Negative

* A new third-party runtime dependency (Redisson) in the platform BOM and in any service that adds
  `starter-lock`.
* Fail-closed means a Redis outage now turns into a `503` on any operation guarded by a lock,
  where today (absent locking) those operations simply run uncoordinated. This is the intended
  tradeoff (Section 5) but is an availability change that must be called out to on-call/SRE (the
  same "alert on this path" treatment the gateway's fail-open rate limiter already gets in
  `security-posture.md`, mirrored here for the fail-closed path).
* Redisson's connection pool and pub/sub channel (used internally for lock-release notification) add
  a small amount of additional Redis load and connection count per service that adopts the starter.
* Two more real consumers (Sprint 17 Features 17.3/17.4) are required before this capability's API
  shape can be considered validated in practice, not just on paper.

## Alternatives Considered

* **Spring Data Redis `RedisLockRegistry` / Spring Integration's JDK lock registry.** Lighter - no
  extra client library, reuses the `LettuceConnectionFactory` services already have via
  `spring-boot-starter-data-redis`. Rejected as the primary mechanism because it has no built-in
  watchdog/auto-renewal (a held lock's TTL must be managed manually or it is fixed-and-unextendable),
  making the variable-duration bill-run case harder to get right safely; Redisson's `RLock` provides
  this out of the box. Noted as a possible lighter-weight fallback if Redisson's footprint proves
  unjustified once real consumers land - a decision revisit trigger, not a rejection of the
  possibility.
* **Hand-rolled `SET key NX PX ttl` plus a Lua compare-and-delete unlock script.** The same pattern
  the gateway rate limiter already uses (atomic Lua script) for a different purpose. Rejected as the
  general-purpose lock primitive: correct Redlock-style locking has enough edge cases (safe unlock by
  owner token, renewal without a race between the extend and a concurrent acquire-after-expiry) that
  re-implementing it is exactly the "reinventing infrastructure" risk ADR-007 exists to prevent, when
  a maintained library already solves it.
* **Postgres advisory locks (`pg_advisory_lock`).** Rejected: each service owns its own database
  (ADR-006, database-per-service), so a Postgres advisory lock only coordinates within one service's
  DB instance - it cannot coordinate across services, and for within-service coordination the
  existing row-level `FOR UPDATE SKIP LOCKED` pattern (already used by `MsisdnPoolRepository`)
  already covers the single-row case. It also does nothing for the bill-run case, where the goal is
  explicitly to take contention *off* the database that the batching work was designed to relieve.
* **ZooKeeper / etcd as a dedicated coordination service.** Rejected: introduces a new stateful
  dependency with no other use in the platform today, purely to solve a problem Redis (already
  present, already the platform's shared-state store) solves adequately at the platform's current
  scale.
* **Fail-open-on-outage (mirroring the gateway rate limiter).** Rejected as the default for the
  reasons in Section 5; the two risk profiles are not equivalent.

## Related ADRs

* ADR-007 Platform Library Strategy (new-module extension rule; this ADR is the module proposal)
* ADR-018 Platform Starter Dependency Model (starter-only consumption rule this module follows;
  Section 6 above records the fit explicitly rather than amending ADR-018)
* ADR-006 Database Strategy (database-per-service; why Postgres advisory locks do not substitute for
  a cross-service/cross-instance lock)
* ADR-009 Event-Driven Architecture / Outbox (the `REQUIRES_NEW`-per-batch bill-run pattern this ADR's
  billing-service consumer builds on, `BillRunBatchProcessor`)
* ADR-011 Security Foundation (owns the gateway rate limiter this ADR contrasts with; the specific
  fail-open behavior itself is documented in `docs/architecture/security-posture.md` Section 6, the
  precedent this ADR deliberately diverges from)
