# Working TODO

Scratchpad for the current task. Write the plan here before implementing, check items off as you go,
and add a short review section when done. This is ephemeral working state - durable status lives in
the sprint READMEs and `STATUS.md`.

## Current task

_(none in progress — Sprint 09 COMPLETE)_

Sprint 09 DONE 5/5. Onboarding saga (AC-01) built across subscription/order/payment. 9.5 scoped
real-broker de-risk proved the Debezium eventType header + caught/fixed a platform-wide saga-breaking
bug (PascalCase outbox aggregate_type). Routing regression gate added. 123 saga tests green. Nothing
committed (user handles git). Next: Sprint 10 (usage-metering) or Sprint 14 full-system AC-01.

### 9.5 - AC-01 End-to-End (DONE, scoped)
Approach: SCOPED real-broker de-risk (user choice); full-system acceptance stays Sprint 14 (14.1).

### 9.5 real-broker de-risk - FINDINGS (in progress)
Brought up infra subset (postgres+kafka+schema-registry+kafka-connect), registered a real Debezium
outbox connector. Results:
- PROVEN GOOD: the `eventType` Kafka header works (`eventType:subscription.activated.v1` on the wire) -
  the 9.4 connector fix is validated against real Debezium 3.1.0.
- FOUND + FIXED (saga-breaking, platform-wide): outbox `aggregate_type` was PascalCase in ALL 7
  services -> Debezium routed to `Subscription.events` while consumers listen on `subscription.events`
  -> zero delivery. tech-lead ruled: lowercase the OUTBOX routing key but KEEP PascalCase for audit
  entity-type (split the overloaded constant). Applied across all 7 emitters + docs (PLATFORM-SPEC 5,
  event-catalog, reference template). Proven live: lowercase routes to `subscription.events` + header.
  saga-3 consolidated build 114 tests green. Infra torn down.
- qa building the mandated routing regression test (EventRouter SMT) + 9.5.1 welcome-log + 9.5.2
  AC-01 traceability (full compose acceptance stays Sprint 14).
- NOTE (out of 9.5 scope): customer-service shows 51/1/5 PRE-EXISTING env test failures
  (Keycloak/Testcontainers unavailable here), verified pre-existing via git-stash; not caused by the
  routing fix. Flag for separate check.

### 9.5 plan
- [ ] Real-broker de-risk (the key unknown): bring up infra subset (postgres+kafka+schema-registry+
      kafka-connect), create+register a Debezium outbox connector, INSERT an outbox_event row, and
      assert via kafka-console-consumer --property print.headers=true that the message lands on
      <aggregate>.events WITH header `eventType:<event>.v1`. Proves the connector event_type->header
      placement against the pinned Debezium 3.1.0. Tear down.
- [ ] 9.5.1 welcome trigger: verify subscription.activated.v1 carries customerId/msisdn/tariffCode
      (+orderId); add a mock welcome-log fallback (notification-service consumer is Sprint 12).
- [ ] 9.5.2 saga integration test (happy + compensation) at the achievable scoped level; mark the
      full-system compose acceptance as Sprint 14 (14.1) in the README honestly.
- [ ] Update Sprint 09 README (9.5) + STATUS.md; sprint 09 -> DONE if 9.5 met at scoped level.

### 9.4 code-review fixes (tech-lead ruled) - DONE

### 9.4 code-review fixes (tech-lead ruled) - DONE
All fixes landed + verified; final code-review APPROVE. order 41 / subscription 52 / payment 21
= 114 tests green (consolidated -am build). 9.4 marked DONE, sprint 4/5. Details below.
9.4 implemented + 105 tests green, but code-review found 2 must-fix correctness defects
(masked by mocked tests). tech-lead ruling -> fix set:
- [ ] PLATFORM (first, platform-engineer): InboxBehavior runs INBOX=350 OUTER to TRANSACTION=400
      -> inbox insert autocommits before handler tx -> rollback loses the saga step. Reorder
      INBOX inner to TRANSACTION so inbox+handler+outbox commit atomically; rollback test.
- [ ] order-service: new GET /internal/orders/{id} (no JWT, no ownership guard, gateway-excluded)
      + system query/handler; permitAll /internal/**; guard Order.fail().
- [ ] subscription: OrderServiceClient -> repoint to /internal/...; map ALL 4xx (incl 401/403)
      to TERMINAL (fail-closed -> activation-failed), only 5xx/IO/circuit-open stay transient;
      route both activation-failed emits through SubscriptionActivationFailedEmitter.
- [ ] consumers (after platform fix): convert AC-01 saga commands to IdempotentRequest keyed by
      messageId, delete manual firstSeen. Set: subscription PaymentCompleted/PaymentFailed,
      order PaymentCompleted/SubscriptionActivated/PaymentRefunded, payment OrderCreated/
      SubscriptionActivationFailed.
- [ ] devops: exclude /internal/** from gateway routing.
- [ ] platform-engineer note: deprecate manual firstSeen-in-consumer; IdempotentRequest is the path.
- [ ] re-verify all 3 services + code-review; THEN mark 9.4 DONE.

### 9.4 plan
- [ ] architecture: design the saga wiring; KEY decision = how tariffCode/version reaches
      activation (payment.completed carries only orderId/customerId; order has tariffId/name;
      catalog GET /tariffs/{id} has code+version). Also reconcile trigger (payment.completed
      vs order.confirmed.v1, which is cataloged-but-unbuilt) + Debezium eventType header.
- [ ] event-integration (if design needs it): any new/changed avsc (e.g. order.confirmed.v1).
- [ ] domain-engineer subscription 9.4.1: payment.completed.v1 inbox consumer + tariff sourcing
      per design -> ActivateSubscriptionCommand; eventType-header filter; idempotent.
- [ ] domain-engineer order 9.4.2: consumers payment.completed.v1 (->PAID) +
      subscription.activated.v1 (->FULFILLED); saga_state updates; eventType filter; idempotent.
- [ ] domain-engineer 9.4.3 compensation (per tech-lead ruling): payment consumes
      subscription.activation-failed.v1 -> RefundPaymentCommand -> payment.refunded.v1; order
      consumes payment.refunded.v1 -> CancelOrderCommand (system actor, bypass ownership guard)
      -> order.cancelled.v1. All inbox-idempotent; no dangling MSISDN.
- [ ] devops/infra: Debezium connector emits event_type as `eventType` header (or suspend/saga
      consumers stay inert). Needed for e2e in 9.5.
- [ ] qa: Testcontainers per-service consumer tests green; code-review; README + STATUS update.

### 9.3 review (DONE)

### 9.3 plan
- [ ] tech-lead ruling: does subscription emit a cataloged `subscription.activation-failed.v1`
      on activation failure (catalog gap: compensation shows payment.refunded->order.cancelled
      with no trigger event)? Determines 9.3 vs 9.4 scope for the failure signal.
- [ ] event-integration: author `subscription.suspended.v1` + `subscription.terminated.v1`
      (activated already exists); +activation-failed if tech-lead approves. Capture field lists.
- [ ] domain-engineer 9.3.1: POST /api/v1/subscriptions (internal/saga) - extend the existing
      ActivateSubscriptionCommandHandler to also emit subscription.activated.v1 (+failure signal
      per ruling); no MSISDN allocated on failure.
- [ ] domain-engineer 9.3.2: suspend/reactivate/terminate endpoints + commands+handlers ->
      subscription.suspended/terminated.v1 (terminate releases MSISDN); payment.failed.v1 inbox
      consumer suspends idempotently after grace.
- [ ] domain-engineer 9.3.3: GET /{id} and GET ?customerId= (paged) -> ApiResult.
- [ ] domain-engineer 9.3.4: MNP state-machine interface/enum scaffold, documented post-MVP (FR-16).
- [ ] thin controller, @PreAuthorize per contract (reads/lifecycle JWT; activate internal).
- [ ] qa: Testcontainers + web-layer tests green; code-review; update README + STATUS together.

### 9.2 review (DONE)

### 9.2 plan (domain-engineer, avsc already authored) - DONE
- [x] 9.2.1 enums: SubscriptionStatus (ACTIVE/SUSPENDED/TERMINATED), MsisdnStatus (FREE/RESERVED/ALLOCATED)
- [x] 9.2.1 Subscription aggregate + state machine; multi-active per customer (FR-14, FR-15)
- [x] 9.2.1 MsisdnPool aggregate (reserve/allocate/release), SimCard aggregate
- [x] 9.2.1 unit tests for state machine (12 Subscription + 7 MsisdnPool)
- [x] 9.2.2 MsisdnPoolRepository (FOR UPDATE SKIP LOCKED), SubscriptionRepository
- [x] 9.2.2 MsisdnAllocationService (domain) reserve->allocate / release
- [x] 9.2.2 Java records MsisdnAllocatedV1 / MsisdnReleasedV1 (epoch millis, named to avsc record name)
- [x] 9.2.2 Activate/Terminate commands + mediator handlers -> outbox emit + audit
- [x] 9.2.2 copy 2 avsc to test resources + schema-compat snapshot test (field-for-field in order)
- [x] tests: boot+flyway, FREE->ALLOCATED + count drop, concurrent no-dup (32 threads), terminate releases + outbox row
- [x] verify: mvn -pl subscription-service -am test BUILD SUCCESS (25 tests, 0 fail)

### 9.3 review (DONE)
Delivered: internal activate (+activated.v1, +activation-failed.v1 on failure with no allocation),
suspend/reactivate/terminate endpoints + events, paged reads, payment.failed.v1 consumer, MNP
scaffold. tech-lead ruling added cataloged subscription.activation-failed.v1 (docs amended by
product-owner; producer 9.3, consumers 9.4.3). All 5 subscription avsc authored; failedAt set to
timestamp-millis (module-consistent; ruling's ISO-string precedent didn't exist).
code-review: 1 MUST-FIX (consumer suspended on SUCCESSFUL payments - filtered on customerId alone)
+ 1 should-fix (nullable orderId vs required avsc). BOTH FIXED BY ME (domain-engineer hit session
limit): consumer now filters on eventType Kafka header + fails closed, +2 regression tests;
orderId required at request+command. Re-verified: 43/43 tests BUILD SUCCESS.
Key 9.4 follow-up: Debezium connector must emit event_type as the `eventType` header or the suspend
path is inert (consumer fails closed). Recorded in sprint README.

### 9.2 review (DONE)
Independently re-ran `mvn -pl subscription-service -am test` -> BUILD SUCCESS, 25/25
(12 + 7 state-machine units, 2 schema-compat, 4 Testcontainers integration incl. full
boot+Flyway and 32-thread no-dup allocation). code-review: APPROVE, no must-fix; 3 carried
follow-ups (unpersisted reserved_until hold -> 9.4; subscription lifecycle events -> 9.3;
seed dev-profile gating) recorded in the Sprint 09 README. Status: 9.2 DONE, sprint 2/5.

### 9.1 (DONE - kept for reference)

## Plan

- [x] 9.1.1 Scaffold `microservices/subscription-service` from the ADR-017 template
      (port 9005, base package `com.telco.subscription`, CQRS+Mediator, audit logging),
      depending on starter-api, -security, -mediator, -observability, -outbox, -inbox;
      owns the `subscription` database. AC: service starts, registers, exposes Swagger UI.
- [x] 9.1.2 Flyway `V1__subscription.sql`: `subscriptions`, `msisdn_pool`, `sim_cards`;
      outbox/inbox come from `classpath:db/migration/platform` (starter migrations V900/V901).
      AC: status ACTIVE/SUSPENDED/TERMINATED; pool FREE/RESERVED/ALLOCATED.
- [x] 9.1.3 Seed MSISDN pool with 1000 FREE numbers (`V2__msisdn_pool_seed.sql`, idempotent).
- [x] Verify (compile): `mvn -pl subscription-service -am compile` SUCCESS.
- [x] Verify (runtime, real Postgres 16): V1+V2 apply; 4 tables; 1000 FREE seeded;
      allocation 1000->999; status CHECKs reject invalid (both tables); partial-unique
      enforces one live sub per MSISDN with reuse-after-terminate. Done via throwaway
      `postgres:16-alpine` + psql; container torn down.
- [ ] Verify (full app boot + Swagger UI): rides with the 9.2 Testcontainers
      schema-migration/integration test (Docker now available).
- [x] Update Sprint 09 README (9.1) and STATUS.md together.

## Review

Feature 9.1 delivered by the microservice-generator agent, mirroring customer-service
(closest analog: CQRS+Mediator, audit-mandated) and order-service.

Created: service `CLAUDE.md` (declares CQRS+Mediator, audit mandate), security config,
CQRS package placeholders, audit scaffolding, `V1__subscription.sql` (subscriptions,
msisdn_pool, sim_cards, audit_log with CHECK constraints + partial-unique index for one
live subscription per MSISDN), `V2__msisdn_pool_seed.sql` (905320000000..999, FREE),
`application-test.yml`. Modified: pom (added starter-mediator/-outbox/-inbox + JPA/Flyway/
test stack, all versions from platform-bom), README, config-server base `application.yml`.
Reactor already listed the module; infra/config wiring for `subscription` pre-existed.

Verified: compile SUCCESS (platform install + `-pl subscription-service -am compile`).
NOT verified (no Docker here): live migration apply / boot / Swagger / allocation count -
deferred to the 9.2 integration test.

Notes for later: (1) audit writer is a local copy flagged for extraction to a platform
starter (platform-capabilities.md) - same as customer-service. (2) Seed runs in all
profiles like identity's seed; domain-engineer may gate behind a dev profile if prod must
start empty. (3) Confirm `docs/api-contracts/subscription-service.md` before 9.3 endpoints.
