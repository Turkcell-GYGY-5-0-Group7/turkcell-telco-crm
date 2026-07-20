# Sprint 24 Handoff (updated 2026-07-20 after the live E2E run)

Branch `feat/sprint-24-pdf-gap-closure`. Progress: **7 of 8 features DONE**; 24.8 is IN PROGRESS -
the live run is complete and documented, one blocking defect remains.

**NOTHING FROM THIS SESSION IS COMMITTED** (user instruction: they may push to another branch).
Uncommitted in the working tree:

- `microservices/order-service/src/test/java/.../OrderCreatedEventSerializationTest.java` - the
  test from commit `07549ea` errored; its local `ItemView` record now declares
  `@JsonIgnoreProperties(ignoreUnknown = true)`, mirroring the real consumer DTOs. With this,
  order-service is 136 tests green and billing-service 90.
- `docs/tasks/sprint-24-pdf-gap-closure/24.8-run-report.md` (new, full evidence)
- Status touches: sprint README, 24.8 feature file, `docs/tasks/STATUS.md`, `docs/tasks/lessons.md`
  (three new lessons), this file.

## What the live run proved

Full detail: [24.8-run-report.md](24.8-run-report.md). Summary: backend sweep **14/15 green** on a
fresh stack, Swagger **7/7**, Idempotency-Key replay idempotent with `method` persisted, and the
browser journey proved the addon path from catalog seeding through the wizard and web-bff down to
the persisted `ADDON` order item (120.00 tariff + 25.00 addon = 145.00). Both bugs fixed in
`07549ea` are confirmed holding live.

## THE ONE BLOCKER

`AddonOnboardingAcceptanceIT` fails: the order sticks at CONFIRMED and never reaches FULFILLED.

Root cause (pre-existing, NOT Sprint 24): order-service's `SubscriptionActivatedEventConsumer`
re-throws when the order is not yet CONFIRMED, expecting Kafka redelivery - but the error handler
is `FixedBackOff(interval=0, maxAttempts=9)`, so ten attempts burn in about a second and the event
is dropped. When it fires: the customer is charged and the line IS active, but the bundled addon
is never provisioned or billed, and the onboarding wizard hangs waiting for FULFILLED.

Fix direction (deliberately not rushed in - it touches the consumer error handling shared by every
service): give that transient path a real non-zero backoff or a retry topic. Grep for
`DefaultErrorHandler`/`FixedBackOff` in the platform starters and the services' Kafka config; the
error surfaced in `telco-order-service` logs as
`Backoff FixedBackOffExecution[interval=0, currentAttempts=10, maxAttempts=9] exhausted`.
Secondary cosmetic finding: `payment.completed.v1` for AC-02's direct invoice payments (random
`orderId` by design) makes order-service throw "Order not found" and burn ten retries - should be
a terminal skip, not an ERROR.

## To finish 24.8

1. Fix the backoff defect above; rebuild order-service (and any other affected image).
2. Boot the stack, **`make -C infra register-connectors` (mandatory after EVERY restart - skipping
   it cost this run a false alarm)**, gate on 11 connectors RUNNING.
3. Re-run the sweep: `mvn -f microservices/pom.xml -pl acceptance-tests -am -Pacceptance verify`.
   Target: 15/15 with `AddonOnboardingAcceptanceIT` green.
4. Optionally re-drive the browser addon journey to see the wizard reach "subscription is active".
5. Then close out: README/STATUS to 8/8, event-catalog Section 3 saga sequences for the addon and
   plan-change flows, `docs/product/requirements.md` FR-09/FR-22 traceability, delete this file.

## Environment facts

- mvn: `/Users/winkoffice/.m2/wrapper/dists/apache-maven-3.9.15/9925cc1d/bin/mvn`; add
  `-Dapi.version=1.44` to any Testcontainers run (Docker Engine 29).
- Fresh-volume boot takes 6-8 minutes to reach 13/13 healthy; volumes kept boots faster.
- Thermal: one contiguous stack-up window, `caffeinate -dims` on long commands, `make infra-down`
  immediately after. Stack is currently DOWN and the frontend dev server is stopped.
- `infra/docker/.env` has `PSP_MOCK_FORCE_OUTCOME=SUCCESS`.
