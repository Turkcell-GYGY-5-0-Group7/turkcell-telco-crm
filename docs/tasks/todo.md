# Working TODO — Sprint 17: Distributed Locking (post-MVP) — COMPLETE (5/5)

Sprints 01-15 are the MVP (feature-complete; one deferred exit-criteria item - the full 13-service
in-cluster boot - remains tracked in `docs/tasks/STATUS.md`'s Sprint 15 entries, user-deferred to a
future dedicated session, not part of this working set). Sprint 16 (Web Frontend) is owned by a
colleague. This working TODO tracked Sprint 17 across two phases: Phase A (ratify ADR-024, build the
platform foundation - 17.1/17.2) and Phase B (adopt it in both real consumers, 17.3/17.4, plus the
docs update, 17.5). Both phases are now done. Mode: plan -> approve -> execute, review at feature
boundaries. Update the owning sprint README + STATUS.md together as each feature reaches DONE; capture
lessons.

## Step 0 — Ratify ADR-024 (gate; no product code until done) [DONE 2026-07-12]
- [x] `architecture` agent validated ADR-024 against ADR-004/007/018 and PLATFORM-SPEC. Found: Section
      5's `LockAcquisitionException extends PlatformException` is not buildable - `PlatformException`
      (`platform-common`) is `sealed` with a `permits` list scoped to its own package, and no
      `module-info.java` exists anywhere under `platform/`, so Java's same-package sealed-subtype rule
      applies. Recommended wrapping the existing `DependencyFailureException` instead of inventing a
      new exception type.
- [x] `tech-lead` agent ratified: adopt the `DependencyFailureException`-wrap (new `LockErrorCode` in
      `platform-core/lock`, zero `starter-api` changes) over the fallback (`LockAcquisitionException`
      as a plain `RuntimeException` + a second, parallel `@RestControllerAdvice` in `starter-lock`) -
      rejected the fallback because it splits error-mapping across two advices for no benefit
      `DependencyFailureException` doesn't already give for free. Module placement, API shape
      (Section 3), TTL/lease semantics (Section 4), and ADR-018 fit (Section 6) confirmed sound as-is.
- [x] Applied the ratified fix: `architecture/adr/ADR-024-distributed-lock-strategy.md` (Status ->
      Accepted; Section 2 and Section 5 rewritten with a ratification note) and
      `docs/tasks/sprint-17-distributed-locking/17.1-starter-lock-module.md` /
      `17.2-distributed-lock-test-support.md` (task descriptions/ACs reworded to match - no
      `LockAcquisitionException` anywhere). Sprint 17 README Exit Criteria updated to record ratification
      met. (17.3/17.4/17.5 task files and the README's own older prose still reference the pre-
      ratification `LockAcquisitionException` name in a few spots - out of scope this session since
      those features are deferred; reword when they are picked up.)

## Step 1 — Feature 17.1: platform-core/lock + starter-lock + BOM + 503 mapping [DONE 2026-07-12]
- [x] 17.1.1 `platform/platform-core/lock` (artifactId `platform-lock`): `DistributedLock` port,
      `LockHandle`, `LockErrorCode` (mirrors `CommonErrorCode`). Zero Spring/Redisson in the dependency
      graph - verified via `dependency:tree`. Registered in `platform-core/pom.xml` modules.
- [x] 17.1.2 `platform/platform-starters/starter-lock`: `RedissonDistributedLock` (watchdog-managed
      when `leaseTime==null`, explicit hard-expiry lease otherwise; fails closed via
      `DependencyFailureException`+`LockErrorCode`), `RedissonLockHandle`, `LockProperties`
      (`telco.platform.lock.*`), `LockAutoConfiguration` (`@ConditionalOnClass`/`@ConditionalOnProperty`/
      `@ConditionalOnMissingBean` override points, `AutoConfiguration.imports` registered). Plain
      `org.redisson:redisson`, not `redisson-spring-boot-starter`, per ADR-024 Section 2. Registered in
      `platform-starters/pom.xml` modules.
- [x] 17.1.3 `platform-bom` pins `redisson.version` (3.50.0) + `org.redisson:redisson`, and adds
      `platform-lock`/`starter-lock` (+ `starter-lock` test-jar) coordinate entries mirroring
      `platform-outbox`/`starter-outbox`. Verified the fail-closed -> 503 path with a dedicated Spring
      context test (`LockDependencyFailureMappingTest`, MockMvc, `@AutoConfigureMockMvc`) proving
      `DependencyFailureException` built with `LockErrorCode.LOCK_ACQUISITION_FAILED` returns HTTP 503
      via the UNCHANGED `GlobalExceptionHandler.handleDependencyFailure` - no handler edit required.
      Also pinned `testcontainers.version=1.20.6` in `platform-bom` (Spring Boot 4.1's own bundled
      testcontainers-bom moved to the renamed 2.x artifact line; mirrors `microservices/pom.xml`'s
      existing, separate pinning of the same version for the same reason).

## Step 2 — Feature 17.2: Testcontainers Redis test support + contention/failure-mode suite [DONE 2026-07-12, code unverified live - see note]
- [x] 17.2.1 `RedisContainerSupport` (singleton-container pattern, `redis:7-alpine`) under
      `starter-lock/src/test/.../testsupport/`, packaged as a `maven-jar-plugin` test-jar mirroring the
      `platform-event-contracts` precedent exactly, pinned in `platform-bom`.
- [x] 17.2.2 Three test classes proving all four ADR-024 guarantees against a real Redis:
      `RedissonDistributedLockContentionTest` (8 threads x 25 iterations on one key; a
      compare-and-set overlap detector proves zero simultaneous critical-section entries; final
      counter equals the exact expected total - no lost updates), `RedissonDistributedLockLeaseSemanticsTest`
      (watchdog-managed lease survives 2.5s past a 2s `watchdogTimeout`, proven by a losing competing
      acquire; an explicit 2s lease hard-expires and a second acquirer succeeds despite the first
      holder never releasing), `RedissonDistributedLockFailureModeTest` (a `RedissonClient` shut down
      after connecting - simulating a lost connection, not an unreachable-at-startup address, since
      Redisson's connection pool connects eagerly at `Redisson.create()` - causes
      `withLock` to throw `DependencyFailureException` and the guarded action's invocation count stays
      zero). Plus `LockAutoConfigurationTest` (enabled-by-default exposes `RedissonClient`+
      `DistributedLock`+`LockProperties` beans; `enabled=false` suppresses them;
      `@ConditionalOnMissingBean` allows override).
- [x] All 6 test files compile clean; `starter-lock`'s spotbugs+checkstyle pass clean (module-level
      `install -DskipTests`); the one non-Docker test (`LockDependencyFailureMappingTest`) passes live.
- [x] Added a 7th, Docker-independent test class (`RedissonDistributedLockUnitTest`, Mockito-mocked
      `RedissonClient`/`RLock`) as a code-review follow-up (see Step 4) - 9/9 passing live, covering the
      lease-mode branching and exception-transparency logic the 4 Testcontainers classes can't currently
      exercise in this sandbox.
- [ ] **NOT independently verified live**: the 4 Testcontainers-Redis-backed classes could not execute
      in this sandbox - Docker Desktop 29.1.2's minimum API floor (1.44) rejects the pinned
      Testcontainers 1.20.6's bundled `docker-java` client (negotiates API 1.32). Confirmed pre-existing
      and repo-wide, not caused by this sprint: the untouched, already-existing `starter-inbox`
      Testcontainers test (`InboxTransactionAtomicityTest`) fails identically. Re-run
      `mvn -f platform/pom.xml -pl platform-starters/starter-lock -am verify` in an environment with a
      compatible Docker version (or after a repo-wide Testcontainers version bump - a separate,
      cross-cutting decision, not undertaken here) to close this out.

## Step 3 — Build verification [DONE 2026-07-12]
- [x] `mvn -f platform/pom.xml -am install -Dschema.registry.skip=true -DskipTests` - full reactor,
      structurally clean (schema-registry's live-endpoint check needs infra not running here; its own
      documented skip flag was used, not a code change).
- [x] `mvn -f platform/pom.xml -pl platform-core/lock dependency:tree` - zero Spring/Redisson.
- [x] `mvn -f platform/pom.xml -pl platform-starters/starter-lock -am install -DskipTests` (spotbugs +
      checkstyle) and `test -Dtest=LockDependencyFailureMappingTest` (the one Docker-independent test) -
      both clean/green.
- Environment note for future sessions in this sandbox: `mvn` on `PATH` resolves to Homebrew's JDK 25
  by default (`JAVA_HOME` unset), which breaks spotbugs (`Unsupported class file major version 69`) -
  export `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` before any platform
  build. See `docs/tasks/lessons.md` 2026-07-12 entry for this and the Testcontainers/Boot-4-test-API
  gotchas hit this session.

## Step 4 — Close out
- [x] code-review (ADR-compliance) on Features 17.1/17.2. First pass: CHANGES REQUIRED - one HIGH
      finding (`withLock(Callable)` rewrapped every domain `RuntimeException` from a guarded action's
      critical section as `IllegalStateException`, which would have broken `GlobalExceptionHandler`'s
      type-based dispatch for 17.3/17.4's future consumers - fixed: only checked exceptions get
      wrapped now, `RuntimeException` propagates unwrapped, matching the `Runnable` overload) and two
      MEDIUM findings (dead `telco.platform.lock.lease-time` config - removed from `LockProperties`
      and ADR-024's property table, since the port's API always takes an explicit per-call `leaseTime`
      and there was no code path that would ever read a configured default; missing Docker-independent
      unit coverage - added `RedissonDistributedLockUnitTest`, 9 Mockito-based tests, including a
      direct regression test for the HIGH finding). One LOW finding (a Javadoc overclaim on
      `RedissonLockHandle.release()`'s resilience) fixed as a comment-only change. Second pass:
      **APPROVE** - independently re-verified all four fixes against the current file contents and by
      re-running the new/affected tests, confirmed no regressions.
- [x] Updated sprint-17 README (Features table DONE/2/5 + Exit Criteria + Deliverables notes) and
      STATUS.md together.
- [ ] Commit the change (offer to user; not yet committed per repo policy - branch first, no auto-push).

## Phase B — Features 17.3, 17.4, 17.5 [DONE 2026-07-12]

Research first (3 parallel Explore agents: subscription-service audit/scheduler patterns,
billing-service bill-run internals, platform docs for 17.5), then plan, then build. Key facts the
task files didn't fully capture, confirmed against the live code: Redis in this stack requires a
password (`infra/docker/compose.yml --requirepass`) but `LockAutoConfiguration`'s host/port fallback
doesn't read it, so both new consumers set `telco.platform.lock.redis.address` explicitly with
embedded credentials; billing-service had no prior Redis usage at all; no `@Scheduled` existed
anywhere in subscription-service before this.

### Step 5 — Feature 17.3: subscription-service MSISDN reaper [DONE]
- [x] `ExpireMsisdnReservationsCommand`/`Result` + `ExpireMsisdnReservationsCommandHandler` (mirrors
      `TerminateSubscriptionCommandHandler`'s mutate-save-audit shape): queries
      `MsisdnPoolRepository.findByStatusAndReservedUntilBefore(RESERVED, now)` (new derived query
      added to the repository), releases each via the existing `MsisdnPool.release()` domain method
      (never raw SQL), writes one `audit_log` row per release via the existing `AuditLogWriter`, all
      atomically inside the mediator's transaction.
- [x] `MsisdnReservationExpiryReaper` (+ `SchedulerConfig` for `@EnableScheduling`, mirroring
      payment-service's pattern): `@Scheduled` tick guarded by an EXPLICIT (non-watchdog) lease
      `DistributedLock` per ADR-024 Section 4's bounded-sweep guidance; package-private `tick()`
      returns the released count or `-1` on lock contention (`DependencyFailureException` +
      `LockErrorCode.LOCK_ACQUISITION_FAILED`), never propagating; any other `DependencyFailureException`
      rethrown.
- [x] `pom.xml` (+ `starter-lock` and its test-jar), `application.yml` (lock + reaper config blocks).
- [x] Tests: 2 fast Mockito unit tests (handler release/audit logic; reaper lock-key/lease/
      error-code-filtering) - both pass live. 1 Testcontainers concurrency IT (3 threads racing
      `tick()` via a `CyclicBarrier`, asserting exact `audit_log` count = no double release) -
      compiles clean, not run live (Docker/Testcontainers limitation, see Phase A Step 2).

### Step 6 — Feature 17.4: billing-service bill-run lock [DONE]
- [x] `RunBillResult` gains a third `runAlreadyOwned` field + a preserved 2-arg constructor (keeps
      `BillingControllerTest`'s direct `new RunBillResult(10, 2)` compiling) + a
      `alreadyOwnedByAnotherPod()` factory (named to avoid colliding with the record's own
      auto-generated `runAlreadyOwned()` accessor - a real compile error hit and fixed this session).
- [x] `RunBillCommandHandler`: `handle()` now wraps the existing orchestration (renamed to
      `runBillRun()`) in a WATCHDOG-managed (`leaseTime=null`) `DistributedLock` keyed on
      `"billing-service:bill-run:" + periodStart + ":" + periodEnd`, per ADR-024 Section 4's guidance
      for variable-duration work (measured 6m20s@100K, Sprint 14.3.2). On `LOCK_ACQUISITION_FAILED`,
      returns `RunBillResult.alreadyOwnedByAnotherPod()` instead of propagating. `BillRunBatchProcessor`'s
      `REQUIRES_NEW` per-batch pattern and `uidx_invoices_sub_period` untouched.
- [x] `pom.xml` (+ `starter-lock` and its test-jar), `application.yml` (billing-service's first-ever
      Redis usage + lock config block).
- [x] Tests: 1 fast Mockito unit test proving the lock key is period-scoped, the lease is
      watchdog-managed, and - directly, via `verify(..., never())`, not just "no duplicate invoices" -
      the losing invocation never reaches `subscriberRepo.findByStatus`/`batchProcessor.processBatch`;
      passes live. 1 Testcontainers concurrency IT (batch-size=1/parallelism=1 to force deterministic
      timing margin, two concurrent `mediator.send(...)` for the same period) - compiles clean, not
      run live (same limitation).

### Step 7 — Feature 17.5: platform docs [DONE]
- [x] `docs/architecture/platform-capabilities.md`: new Section 1 "Distributed locking (via
      `starter-lock`)" subsection; confirmed Section 3 never listed it (nothing to remove).
- [x] `platform/PLATFORM-SPEC.md`: new `## 7. platform-lock` section (Sections 7-11 renumbered to
      8-12 to make room; repo-wide grep confirmed no external cross-reference to the moved section
      numbers exists) + new `### 10.7 starter-lock` subsection + Section 1 coordinate-table rows.
- [x] `docs/architecture/platform-gap-closing-plan.md`: new "Follow-up (shipped after the Tier plan)"
      section (no existing convention for this - confirmed via research, created fresh) recording the
      capability and its first two consumers.

### Step 8 — code-review (17.3/17.4) [DONE]
- [x] First pass: CHANGES REQUIRED. HIGH finding: `telco.platform.lock.enabled: false` in each
      service's shared `application-test.yml` (added to avoid needing live Redis in unrelated tests)
      left NO `DistributedLock` bean at all once disabled (`LockAutoConfiguration`'s
      `@ConditionalOnProperty` gates the whole class) - but the new reaper/handler have MANDATORY
      constructor-injected `DistributedLock`, so this would have broken every pre-existing
      Spring-context test in both modules (confirmed: Redisson connects eagerly at startup, unlike
      `starter-kafka`'s tolerant listener containers - not caught live here since Testcontainers can't
      run at all in this sandbox, caught by static reasoning about the conditional + constructor
      wiring). MEDIUM finding: the new `@Scheduled` reaper would then fire unconditionally in every
      such context once the bean existed, racing unrelated tests' `msisdn_pool` assertions.
- [x] Fixed: a second, inverse-conditioned `@AutoConfiguration` (`InMemoryDistributedLockAutoConfiguration`,
      `@ConditionalOnProperty(..., havingValue="false")`) packaged in `starter-lock`'s own test-jar,
      supplying a trivial real in-JVM `DistributedLock` (`ConcurrentHashMap<String, ReentrantLock>`)
      whenever the real one is disabled - registered via a SECOND `AutoConfiguration.imports` under
      `src/test/resources`, confirmed packaged into the test-jar artifact via `unzip -l`. Zero changes
      needed to any pre-existing test file in either service. `MsisdnReservationExpiryReaper` gained
      its own `@ConditionalOnProperty(telco.subscription.msisdn-reaper.enabled, matchIfMissing=true)`
      gate, set `false` in the test profile, re-enabled explicitly by the concurrency IT.
- [x] New live-passing test: `InMemoryDistributedLockAutoConfigurationTest` (3 tests, `ApplicationContextRunner`)
      proves the conditional activates only when the lock is explicitly disabled.
- [x] Second pass: **APPROVE** - independently re-verified both fixes against current file contents,
      re-ran the new test live, confirmed no ADR-018 violation (still only these 2 services depend on
      `starter-lock`) and no bean-conflict/ordering risk between the two mutually-exclusive conditionals.
- [x] Lesson captured in `docs/tasks/lessons.md` (2026-07-12): the general pattern ("an optional
      starter with a mandatory consumer needs a substitute bean under the disabled condition too, not
      just permission to be absent") for reuse if a future sprint hits the same shape.

## Deferred / still open
- Re-verifying Feature 17.2's four platform-level Testcontainers behaviors AND the two new
  17.3/17.4 concurrency ITs live, once Docker/Testcontainers compatibility in the execution
  environment is resolved (or a repo-wide Testcontainers version bump is made - a separate,
  cross-cutting decision, not undertaken here).
- Commit the change (offer to user; not yet committed per repo policy - branch first, no auto-push).

---

## Reference: still-open Sprint 15 exit-criteria tail (not part of this working set)

Sprint 15's platform-level exit criterion ("all MVP acceptance criteria hold in the DEPLOYED
environment") is not yet fully met: the full 13-service in-cluster boot (build+deploy the 9 missing
domain service images, register the 10 Debezium outbox connectors, run the AC-01/02/03 acceptance
suite against the deployed cluster) remains the one open item. User-deferred 2026-07-12 to a dedicated
session (multi-hour build + single-node capacity risk). Full detail, evidence, and the exact sub-steps
are preserved in `docs/tasks/STATUS.md`'s 2026-07-12 Sprint 15 entries and
`docs/tasks/sprint-15-deployment/README.md`'s Exit-Criteria Follow-Ups section - resume from there
when scheduled, not from scratch.
