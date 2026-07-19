# Sprint 24 - PDF Gap Closure

| Status | Progress | Last updated |
| --- | --- | --- |
| IN PROGRESS | 6/8 | 2026-07-20 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Close every literal gap between the delivered platform and the MVP spec PDF, per the instructor's
"nothing may be missing" delivery bar (extending beyond the spec is allowed; scope-out items stay
out). The 2026-07-18 audit found all MUST-level items, acceptance scenarios, and NFR targets met;
what remains is: FR-09 (addon and plan-change orders), FR-22 (addon/VAS invoice lines), FR-03
(customer contact info), FR-25 (payment method), per-service Swagger UI, small API-standard items
(sort param, payments Idempotency-Key header, type-conditional TCKN/VKN), and AC 14.3 step 15
(quota-exceeded SMS must suggest an addon).

Design decisions and accepted precedents: [design-note.md](design-note.md).

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 24.1 | Catalog addon management (allowances, seeds, create API, internal snapshot) | DONE | [24.1-catalog-addon-management.md](24.1-catalog-addon-management.md) |
| 24.2 | Order model generalization (order/item types, validation matrix, invariant relaxation) | DONE | [24.2-order-model-generalization.md](24.2-order-model-generalization.md) |
| 24.3 | Addon purchase flow (addon.purchased.v1, quota top-up, invoice lines, web-bff forwarding) | DONE | [24.3-addon-purchase-flow.md](24.3-addon-purchase-flow.md) |
| 24.4 | Plan change flow (subscription.tariff-changed.v1, changeTariff, quota re-provision) | TODO | [24.4-plan-change-flow.md](24.4-plan-change-flow.md) |
| 24.5 | Customer contact info + type-conditional TCKN/VKN validation | DONE | [24.5-customer-contact-and-conditional-id-validation.md](24.5-customer-contact-and-conditional-id-validation.md) |
| 24.6 | Payment method + Idempotency-Key header | DONE | [24.6-payment-method-and-idempotency-header.md](24.6-payment-method-and-idempotency-header.md) |
| 24.7 | API polish: Swagger x7, sort param, quota-exceeded template | DONE | [24.7-api-polish-swagger-sort-notification.md](24.7-api-polish-swagger-sort-notification.md) |
| 24.8 | Tests and full E2E re-validation | TODO | [24.8-tests-and-e2e-revalidation.md](24.8-tests-and-e2e-revalidation.md) |

Execution order: 24.5/24.6/24.7 (independent quick wins, any order) -> 24.1 -> 24.2 -> 24.3/24.4
(parallel once 24.2 lands) -> 24.8.

## Sprint Deliverables

- Addon ordering end to end: catalog admin create + seeded addons, bundled-with-onboarding and
  standalone addon orders, quota top-up, addon invoice lines, working frontend addon selection.
- Plan-change orders end to end: order-driven tariff switch, quota re-provision, billing update.
- Customer email/phone; payment method enum; conditional TCKN/VKN; Swagger UI on all services;
  sort pagination; payments Idempotency-Key header; corrected quota-exceeded SMS.
- Three new acceptance ITs plus a full fresh-stack E2E re-validation (Sprint 14.6 pattern).

## Exit Criteria

- Every FR sentence in the MVP PDF is either implemented or covered by the PDF's own Scope-Out
  list (MNP, prepaid, corporate-beyond-registration, BTK, roaming; Wallet aggregate documented
  as out in the design-note).
- New acceptance ITs green in the same sweep as AC-01/02/03, campaign, and BFF suites.
- Browser onboarding with addon selection works end to end; Swagger UI reachable on all 12
  Spring services.
