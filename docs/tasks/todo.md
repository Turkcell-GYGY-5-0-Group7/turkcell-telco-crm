# Working TODO - Sprint 24: PDF Gap Closure

Goal: close every literal requirement gap against the MVP spec PDF ("nothing may be missing";
extending allowed, scope-out stays out). Plan approved 2026-07-19; full plan at
`~/.claude/plans/i-want-to-test-shimmying-hippo.md`; decisions in
`docs/tasks/sprint-24-pdf-gap-closure/design-note.md`. Branch:
`feat/sprint-24-pdf-gap-closure` (off master post-#34). Commit after every feature.

Thermal rules: code with the stack DOWN; single contiguous stack-up window for 24.8's live
verification; caffeinate-wrapped long commands; `make infra-down` immediately after.

## Phase 0 - Scaffolding
- [x] master synced (merge #34 verified), branch created
- [x] sprint-24 docs (README, design-note, 8 feature files), STATUS.md row, this file
- [x] scaffolding committed

## Phase 1 - Quick wins (independent)
- [x] 24.7a Swagger deps + security permits in 7 services; commit
- [x] 24.5a @ValidIdentityForType (TCKN/VKN by type); commit with 24.5b
- [x] 24.5b customer email/phone (V2, DTOs, avro additive, event-catalog); commit
- [x] 24.6 payment method enum + V5 + Idempotency-Key header; commit
- [x] 24.7b QUOTA_EXCEEDED template + update-if-different seeder; commit with 24.7c
- [x] 24.7c sort param on main list endpoints; commit

## Phase 2 - 24.1 Catalog addon management
- [x] V2 allowances + seeds; CreateAddonCommand/handler; POST /api/v1/addons; internal snapshot;
      contract doc; unit tests; commit

## Phase 3 - 24.2 Order model generalization
- [x] V8 order/item types; validation matrix; subscription internal read + client; one-line
      invariant relaxed (two-TARIFF still fails); avro additive + compat; internal DTO mirrors;
      unit tests; commit

## Phase 4 - 24.3 Addon purchase flow
- [x] addon-purchased.avsc + subject + compat + catalog row; order fulfillment publish legs +
      standalone ADDON saga branch; usage addAllowance/TopUpQuota + consumer; billing
      addon_charge_record + consumer + invoice lines; web-bff forwards addonCodes; tests; commit
      (order 131 / subscription 84 / usage 90 / billing 81 / web-bff 32 green; V9 added
      addon_type+currency snapshot so the event publishes without a runtime catalog hop)

## Phase 5 - 24.4 Plan change flow
- [x] subscription-tariff-changed.avsc + subject + compat + catalog row; changeTariff +
      ChangeTariffCommand + consumer branch; usage re-provision; billing tariffCode update;
      order fulfill consumer; contract docs; tests; commit
      (order 135 / subscription 92 / usage 99 / billing 90 green; consumers dedup on orderId
      business keys - the tariff-changed record key is the subscriptionId and repeats across
      successive plan changes)

## Phase 6 - 24.8 Tests + E2E re-validation
- [ ] Three new acceptance ITs + two-TARIFF regression
- [ ] Fresh-stack full sweep + browser onboarding with addon + Swagger spot-check +
      Idempotency-Key replay; run report in sprint README
- [ ] Closeout: README/STATUS/event-catalog/api-contracts/requirements traceability; commit
