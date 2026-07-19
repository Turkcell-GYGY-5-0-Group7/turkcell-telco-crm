# Sprint 24 Handoff (session ended 2026-07-19/20)

State snapshot for whoever resumes this sprint. Branch: `feat/sprint-24-pdf-gap-closure`
(created off master after PR #34 merged; NOT pushed yet). Progress: **5 of 8 features DONE and
committed**; 24.3 was mid-implementation by a background agent when the session ended.

## Commits on this branch (oldest first)

| Commit | Feature |
| --- | --- |
| 852c3fd | Sprint scaffolding (README, design-note, 8 feature files, STATUS row) |
| d32e228 | 24.7a - Swagger UI on all services (springdoc in 7 poms + order security permit) |
| 2d8f0a5 | 24.5 - customer email/phone + type-conditional TCKN/VKN (111 tests green) |
| 0194fd5 | 24.6 - payment method enum + Idempotency-Key header (72 tests green) |
| 5b24d19 | 24.1 - addon catalog: allowance columns, 5 seeded addons, real POST /api/v1/addons, internal snapshot (67 tests green) |
| 93f9968 | 24.7b/c - sort pagination on 6 list endpoints + quota-exceeded SMS addon suggestion with update-if-different seeder |
| e64373e | 24.2 - order model generalization: order/item types, validation matrix, saga invariant relaxation, additive order-created.avsc, internal subscription read (order 119 / subscription 83 / payment 72 / campaign 104 green) |

## What was IN FLIGHT when the session ended: 24.3 (addon purchase flow)

A background agent was implementing 24.3 per
[24.3-addon-purchase-flow.md](24.3-addon-purchase-flow.md). It had stalled once with a CLEAN tree,
was resumed, and may or may not have written files before the session died.

**First step on resume: run `git status`.**
- If the tree is clean: nothing survived; re-run the whole 24.3 scope from the feature file.
- If the tree has changes: they are 24.3 work-in-progress. Inventory them against the 24.3 task
  list (schema, order publish legs, subscription skip, usage top-up, billing charges, web-bff
  forwarding), finish what is missing, run the per-module suites, then commit as one
  `feat(sprint-24): addon purchase flow end to end (24.3)` commit.

## Remaining work, in order

1. **24.3** - addon purchase flow. Full spec in the feature file; design decisions in
   [design-note.md](design-note.md) D1/D3/D4. Critical implementation notes discovered so far:
   - subscription-service `PaymentCompletedEventConsumer` must SKIP orderType ADDON orders
     (today a zero-TARIFF order would emit UNSUPPORTED_MULTI_ITEM_ORDER and trigger compensation).
   - New event rides topic `addon.events` via aggregate_type `addon` (Debezium EventRouter routes
     by aggregate_type; no connector change, but the acceptance stack must have the order
     connector running).
   - usage-service top-up when no quota row exists yet: THROW transient so Kafka redelivers
     (activation provisioning may still be in flight) - mirrors order-service's
     TRANSIENT/TERMINAL split.
   - 24.2 already snapshots addon price/name/allowances onto order_items; addon display name is
     in the tariff_name column; event tariffId/tariffName generalize to addon id/name.
2. **24.4** - plan change flow. Spec: [24.4-plan-change-flow.md](24.4-plan-change-flow.md) +
   design-note D2/D4. 24.2 already provides everything it needs (PLAN_CHANGE order validation,
   orderType on the internal order read, subscription internal endpoint). Sequential after 24.3
   (same modules).
3. **24.8** - tests + full E2E re-validation + closeout. Spec:
   [24.8-tests-and-e2e-revalidation.md](24.8-tests-and-e2e-revalidation.md). Three new acceptance
   ITs (AddonOnboarding, StandaloneAddonPurchase, PlanChange), then the Sprint 14.6-pattern
   fresh-stack run: `make infra-destroy` -> rebuild images -> boot -> `make infra-connectors` ->
   full acceptance sweep -> browser onboarding WITH addon selection -> Swagger spot-check on the
   7 newly-enabled services -> payments Idempotency-Key replay. Close out README/STATUS/
   event-catalog/api-contracts/requirements traceability. Then update this file or delete it.

## Environment facts a resuming session must know

- mvn is NOT on PATH: `/Users/winkoffice/.m2/wrapper/dists/apache-maven-3.9.15/9925cc1d/bin/mvn`.
- Every Testcontainers-running Maven invocation needs `-Dapi.version=1.44` (Docker Engine 29
  rejects the client's default API version). Typical:
  `mvn -f microservices/pom.xml -pl <module> -am test -Dschema.registry.skip=true -Dapi.version=1.44`
- Avro/platform rebuild after touching platform-event-contracts:
  `mvn -f platform/pom.xml -pl platform-event-contracts install -DskipTests -Dschema.registry.skip=true`
- Event rules: canonical .avsc + pom `<subjects>` + compat test + event-catalog row; outbox
  publish; inbox dedup INSIDE handler tx (InboxBehavior); every new @KafkaListener gets its OWN
  groupId and filters on the eventType header; Avro changes additive nullable-with-default only.
- Thermal constraint (machine overheats and sleeps): code with the stack DOWN, one contiguous
  stack-up window for live verification, wrap long live commands in `caffeinate -dims`,
  `make infra-down` immediately after. Docker Desktop is started manually by the user.
- `infra/docker/.env` has `PSP_MOCK_FORCE_OUTCOME=SUCCESS` (deterministic saga polling).
- Commit after EVERY feature (user instruction), conventional messages, do not push until asked.

## Where everything is specified

- Approved plan: `~/.claude/plans/i-want-to-test-shimmying-hippo.md`
- Working checklist: `docs/tasks/todo.md` (kept current through 24.2)
- Sprint status: [README.md](README.md) (5/8) + `docs/tasks/STATUS.md` sprint-24 row
- Audit that defined the gaps: `docs/tasks/sprint-14-testing-and-hardening/14.6-post-sprint21-e2e-retest.md`
  (E2E baseline) + the conversation's PDF audit (summarized in the sprint README Objective)
