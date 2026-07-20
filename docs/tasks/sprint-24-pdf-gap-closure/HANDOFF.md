# Sprint 24 Handoff (last updated 2026-07-20, after 24.8a - E2E window pending)

State snapshot for whoever resumes this sprint. Branch: `feat/sprint-24-pdf-gap-closure`
(tracks origin; commits after 6bf685f are LOCAL ONLY - do not push until asked).
Progress: **7 of 8 features DONE and committed**; only 24.8 (tests + E2E + closeout) remains.
Approved plan: `~/.claude/plans/i-was-started-to-iridescent-bubble.md`.

## Commits on this branch (oldest first)

852c3fd scaffolding | d32e228 24.7a | 2d8f0a5 24.5 | 0194fd5 24.6 | 5b24d19 24.1 |
93f9968 24.7b/c | e64373e 24.2 | a8a5a18 **24.3** | (HEAD) **24.4**

Suite baselines after 24.4 (all green, Testcontainers, stack down):
order 135 / subscription 92 / usage 99 / billing 90 / web-bff 32.

## What 24.3 + 24.4 delivered (facts a resuming session needs)

- **addon.purchased.v1** (topic `addon.events`, outbox aggregate_type `addon`, aggregate_id =
  ORDER-ITEM id - unique per event, safe as inbox key). Published by order-service once per ADDON
  item at fulfillment: bundled NEW_LINE orders publish in `FulfillOrderCommandHandler` (with the
  activation payload's subscriptionId), standalone ADDON orders confirm AND fulfill inside
  `ConfirmOrderCommandHandler` (saga step `ADDON_FULFILLED`, no activation leg -
  subscription-service skips ADDON orders). V9 added `order_items.addon_type`/`currency`.
  usage tops up quota (`Quota.addAllowance`, flags re-armed); billing records
  `addon_charge_records` (price = unit * quantity) billed as one line per charge on the next
  bill run (billed flag flips in the bill-run tx).
- **subscription.tariff-changed.v1** (rides `subscription.events` as its THIRD event type;
  produced by `ChangeTariffCommandHandler` after the `payment.completed.v1` PLAN_CHANGE branch).
  **The record key is the subscriptionId (outbox aggregate_id) and REPEATS across successive
  plan changes - every consumer of this event dedups on orderId business keys:**
  usage `"reprovision-quota:" + orderId` (quota reset via `Quota.reprovision`, remaining =
  max(0, new - used), flags recomputed), billing `"billing-tariff-changed:" + orderId`
  (`SubscriberBillingRecord.changeTariff`), order `"plan-change-fulfill:" + orderId`
  (reuses `FulfillOrderCommand`). Terminal changeTariff failures REUSE
  `subscription.activation-failed.v1` (documented reuse, D2) so the existing refund/cancel
  compensation runs.
- Billing consumer tests are named `*Test` (NOT `*IT` - surefire skips `*IT`; two files were
  renamed for this: `AddonPurchasedBillingConsumerTest`, `TariffChangedBillingConsumerTest`).
- Contract docs updated: order-service.md (events table + "Saga fulfillment per order kind"),
  subscription-service.md (payment.completed branching + tariff-changed event), event-catalog
  registry rows for both events. README 7/8, STATUS 7/8, todo.md Phases 4-5 checked.

## REMAINING: 24.8b + 24.8c (spec: 24.8-tests-and-e2e-revalidation.md)

1. **24.8a - DONE (compiled with `-Pacceptance test-compile`, runs only against the live
   stack):** `addon/AddonOnboardingAcceptanceIT`, `addon/StandaloneAddonPurchaseAcceptanceIT`,
   `planchange/PlanChangeAcceptanceIT` + new GatewayApi helpers (createAddon,
   createOrderWithAddons, createStandaloneAddonOrder, createPlanChangeOrder). The two-TARIFF
   compensation regression ALREADY EXISTS and still applies post-24.2:
   `ac01/NewSubscriberOnboardingCompensationAcceptanceIT` (two TARIFF items ->
   UNSUPPORTED_MULTI_ITEM_ORDER -> refund -> CANCELLED). Acceptance sweep command:
   `mvn -f microservices/pom.xml -pl acceptance-tests -am -Pacceptance verify`.
2. **24.8b - fresh-stack E2E (SINGLE stack-up window; ASK THE USER before starting - they start
   Docker Desktop and must approve the thermal window):** `make infra-destroy` -> rebuild
   images -> boot -> `make infra-connectors` (order connector must be registered so
   `addon.events` routes) -> full acceptance sweep (AC-01/02/03 + campaign + BFF + 3 new ITs)
   -> browser onboarding WITH addon -> Swagger spot-check on the 7 newly-enabled services ->
   payments Idempotency-Key replay. `caffeinate -dims` on long commands; `make infra-down`
   IMMEDIATELY after. Append run report to sprint README.
3. **24.8c - closeout:** README 8/8 + run report; STATUS.md; todo.md Phase 6; event-catalog
   Section 3 saga sequences (addon + plan-change); requirements.md FR-09/FR-22 traceability;
   lessons.md if corrections arose; finalize or delete this HANDOFF. Final commit. NO push
   until asked.

## Environment facts (verified)

- mvn: `/Users/winkoffice/.m2/wrapper/dists/apache-maven-3.9.15/9925cc1d/bin/mvn`
- Suites: `mvn -f microservices/pom.xml -pl <module> test -Dschema.registry.skip=true -Dapi.version=1.44`
  (Docker Engine 29 needs the api.version flag; wrap long runs in `caffeinate -dims`).
- Platform rebuild after avsc changes:
  `mvn -f platform/pom.xml -pl platform-event-contracts install -DskipTests -Dschema.registry.skip=true`
- New consumers: own groupId + fail-closed eventType header filter + IdempotentRequest command
  dedup (InboxBehavior); never manual firstSeen; never the record key when the aggregate can
  emit the same event type twice.
- In tests a `@MockitoBean InboxService` silently skips every IdempotentRequest (firstSeen
  defaults false) - use the real inbox or seed state directly.
- `infra/docker/.env` has `PSP_MOCK_FORCE_OUTCOME=SUCCESS`. Commit per feature, no emojis.
