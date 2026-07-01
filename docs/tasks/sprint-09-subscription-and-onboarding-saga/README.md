# Sprint 09 - Subscription and Onboarding Saga

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 5/5 | 2026-07-01 |

> 9.1 verification note: code-complete and compiles (`mvn -pl subscription-service -am compile`
> SUCCESS). Schema/seed runtime ACs verified against a real Postgres 16 (V1+V2 apply; 4 tables; 1000
> FREE MSISDNs; allocation drops FREE 1000->999; status CHECK constraints reject invalid values on
> both tables; partial-unique index enforces one live subscription per MSISDN with reuse-after-
> terminate). Full Spring Boot context boot + Swagger UI rides with the 9.2 Testcontainers
> schema-migration/integration test.
>
> 9.2 verification note: domain + atomic MSISDN allocation delivered and verified. `mvn -pl
> subscription-service -am test` -> BUILD SUCCESS, 25/25 tests (12 Subscription state-machine, 7
> MsisdnPool, 2 schema-compat, 4 Testcontainers integration incl. full boot+Flyway and a 32-thread
> concurrent-allocation no-duplicate test - this also closes the carried-over 9.1 app-boot/Swagger AC).
> MSISDN events emitted via outbox (`msisdn.allocated.v1`/`msisdn.released.v1`), Avro contracts
> authored + registered in the compat gate. code-review verdict: APPROVE, no must-fix.
>
> Carried follow-ups for 9.3/9.4 (from code-review + domain-engineer):
> - `reserved_until` hold is not persisted (reserve->allocate happen in one transaction). Fine for
>   9.2; when 9.4 splits reserve/allocate across saga steps, persist the hold and add a sweeper to
>   reclaim expired RESERVED rows to FREE.
> - 9.3 must add the `subscription.activated.v1` (exists in avro) + `subscription.suspended.v1` /
>   `subscription.terminated.v1` events so billing/notification consumers get the lifecycle events
>   (event-catalog lines 47-49); 9.2 only emits the MSISDN events by design.
> - V2 MSISDN seed runs in all profiles; gate behind a dev profile if prod must start empty.
>
> 9.3 verification note: lifecycle endpoints + events + read queries + payment-failed consumer + MNP
> scaffold delivered. `mvn -pl subscription-service -am test` -> BUILD SUCCESS, 43/43 tests. tech-lead
> ruled the activation-failure compensation trigger is a cataloged event `subscription.activation-failed.v1`
> (event-catalog Sections 2+3 amended by product-owner; producer in 9.3, consumers in 9.4.3). All five
> subscription Avro contracts authored/registered. code-review found and we fixed: (1) MUST-FIX - the
> payment.failed consumer filtered on customerId alone and would have suspended a line on a SUCCESSFUL
> payment (payment.events carries completed/refunded/failed); now filters on the canonical `eventType`
> Kafka header and fails closed, with regression tests for completed.v1 and header-less messages;
> (2) the activation-failed event could emit a null orderId vs the required Avro field - orderId is now
> mandatory at the request and command. avsc `failedAt` was set to timestamp-millis (module-consistent;
> the tech-lead ruling's ISO-string rationale cited a non-existent payment-failed/refunded avsc).
>
> Carried follow-ups for 9.4 (saga wiring):
> - INFRA (must, or the suspend path never fires e2e): the Debezium outbox connector must emit the
>   outbox `event_type` as the Kafka header `eventType` that the payment-failed consumer filters on
>   (e.g. `transforms.outbox.table.fields.additional.placement=event_type:header:eventType`). Cover with
>   an e2e test in 9.5. The consumer fails closed today (ignores untyped messages), so this is safe but
>   inert until wired.
> - payment.failed consumer correlates by `customerId` (not `orderId`) and treats receipt as post-grace
>   (immediate suspend); a subscription-side timed grace deferral is deferred. order-service `payment.events`
>   sibling consumer has the same need for header-based type filtering once multiple types share its topic.
> - 9.4.3 adds the `subscription.activation-failed.v1` consumers in payment (refund) and order (cancel);
>   the saga-initiated cancel must run as a system actor and bypass CancelOrderCommandHandler's ownership guard.
>
> 9.4 verification note: choreographed saga wired across subscription/order/payment. Trigger
> `payment.completed.v1` -> subscription makes ONE sync hop to order `GET /internal/orders/{id}` (no JWT,
> gateway-excluded, no ownership guard) for the tariff snapshot -> ActivateSubscriptionCommand ->
> `subscription.activated.v1` (now carries orderId). order: payment.completed->CONFIRMED,
> subscription.activated->FULFILLED (new Order state + fulfill()), payment.refunded->CANCELLED
> (widened cancel + CompensateOrderCommand, system actor). Compensation: payment consumes
> activation-failed -> refund -> order cancelled. Consolidated build order 41 / subscription 52 /
> payment 21 = 114 tests green.
>
> code-review found and we FIXED 3 correctness defects (incl. 1 latent platform bug) before DONE:
> (1) MUST-FIX dead-loop - the unauthenticated sync hop would 401 and be misclassified as a transient
> outage -> infinite Kafka retry. Fixed: order `/internal/**` permitAll + gateway-deny + distinct
> unguarded system query; OrderServiceClient maps ALL non-404 4xx to TERMINAL (fail-closed ->
> activation-failed), only 5xx/IO/circuit-open stay transient.
> (2) MUST-FIX inbox non-atomicity (PLATFORM, pre-existing since Sprint 02): InboxBehavior ran INBOX=350
> OUTER to TRANSACTION=400, so the inbox row autocommitted before the handler tx -> a rollback silently
> dropped the saga step. Fixed in platform (INBOX->450, inner to TX; rollback-atomicity test); all AC-01
> saga consumers migrated from manual firstSeen to IdempotentRequest so dedup+write+outbox commit
> atomically. Manual firstSeen-in-consumer is now deprecated platform-wide.
> (3) minor: Order.fail() guarded; single SubscriptionActivationFailedEmitter for the failed event.
> Final code-review verdict: APPROVE, both must-fixes resolved with regression tests.
>
> Carried follow-ups for 9.5 (AC-01 e2e) / debt:
> - INFRA (must, e2e): verify against a REAL broker that the Debezium connector emits the `eventType`
>   header (consumers fail closed without it) - the whole saga is inert until proven end to end.
> - 9.5 is the real-broker AC-01: happy path (order->pay->activate->FULFILLED + welcome) and compensation
>   (forced activation failure -> refund -> order CANCELLED, no dangling MSISDN), idempotent under redelivery.
> - platform debt: deprecate/clean remaining manual firstSeen usages; service-level inbox-dedup test for a
>   no-state-guard handler (payment charge) would prove dedup beyond the platform test.
> - pre-existing ARC-09 nit: non-ASCII arrows in payment PaymentRetryScheduler comments (Sprint 08).

> 9.5 verification note (SCOPED real-broker de-risk; full-system acceptance = Sprint 14 / 14.1, per
> "AC-01: 09 built, 14 validated"). Brought up the real infra subset (Postgres+Kafka+Schema-Registry+
> Kafka-Connect/Debezium 3.1.0) and registered a live outbox connector. This PROVED the Debezium
> `eventType` header works AND caught a saga-breaking, platform-wide bug: outbox `aggregate_type` was
> PascalCase in ALL 7 emitters, so Debezium routed to `Subscription.events` while consumers listen on
> `subscription.events` -> zero delivery. Fixed per tech-lead ruling (lowercase OUTBOX routing key;
> PascalCase kept for audit entity-type - split the overloaded constant) across order/payment/
> subscription/customer/product-catalog/identity/reference; convention documented in PLATFORM-SPEC 5,
> event-catalog, and the reference template. Deliverables: (1) a routing REGRESSION GATE per saga
> service that drives the real handler -> real persisted outbox_event row -> real Debezium EventRouter
> SMT and asserts lowercase-topic routing + the eventType header (proven to fail on a PascalCase
> regression); (2) 9.5.1 subscription.activated.v1 payload verified (customerId/msisdn/tariffCode/
> orderId) + a mock welcome-log firing; (3) 9.5.2 AC-01 traceability doc
> ([9.5-ac-01-traceability.md](9.5-ac-01-traceability.md)) mapping each happy + compensation hop to its
> proving test. Consolidated build order 43 / subscription 57 / payment 23 = 123 tests green.
>
> Sprint 09 carried follow-ups (for Sprint 14 / debt):
> - Sprint 14 (14.1): full-system compose AC-01 - all services containerized + Debezium connectors +
>   Keycloak-driven REST driving register->KYC->order->pay->activate->FULFILLED + compensation e2e.
> - platform debt: optional fail-fast in DefaultOutboxService rejecting non-lowercase aggregateType;
>   the routing harness duplicates the connector SMT config (kept in lockstep by comment) - centralize.
> - ENV: customer-service (Sprint 06) shows pre-existing env test failures here (Keycloak/Testcontainers
>   unavailable) - verify against the full stack; not caused by Sprint 09.

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Build subscription-service (9005) with its lifecycle state machine and MSISDN allocation, then wire
the end-to-end new-line onboarding saga (order -> payment -> subscription -> fulfillment + welcome
SMS) including compensation on failure. Completing this sprint delivers acceptance criterion AC-01.

Covers FR-13, FR-14, FR-15 (FR-16 MNP is post-MVP, scaffolded only) and the saga orchestration of
FR-10/FR-12.

## Included Epics

- Epic 9: Subscription Lifecycle and Onboarding Saga (subscription-service + cross-service saga)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 9.1 | Subscription Service - Scaffold and Schema | DONE | [9.1-subscription-service-scaffold-and-schema.md](9.1-subscription-service-scaffold-and-schema.md) |
| 9.2 | Subscription Service - Domain | DONE | [9.2-subscription-service-domain.md](9.2-subscription-service-domain.md) |
| 9.3 | Subscription Service - Application and Lifecycle Endpoints | DONE | [9.3-subscription-service-application-and-lifecycle-endpoints.md](9.3-subscription-service-application-and-lifecycle-endpoints.md) |
| 9.4 | Onboarding Saga Wiring | DONE | [9.4-onboarding-saga-wiring.md](9.4-onboarding-saga-wiring.md) |
| 9.5 | AC-01 End-to-End | DONE | [9.5-ac-01-end-to-end.md](9.5-ac-01-end-to-end.md) |

## Sprint Deliverables

- subscription-service (9005): lifecycle state machine, atomic MSISDN allocation/release, activation
  and lifecycle endpoints, MNP scaffold (deferred), and subscription events.
- Fully wired onboarding saga across order/payment/subscription with compensation.
- AC-01 end-to-end integration test (happy path and compensation).

## Exit Criteria

- AC-01 passes end to end: a registered, KYC-approved customer orders a postpaid tariff, pays via
  mock PSP, gets an automatically activated subscription with an allocated MSISDN, a welcome signal,
  and a FULFILLED order.
- A forced activation failure compensates (refund + order CANCELLED) with no dangling MSISDN.
- FR-13, FR-14, FR-15 pass; FR-10 and FR-12 saga behavior validated; FR-16 scaffolded as post-MVP.
</content>
