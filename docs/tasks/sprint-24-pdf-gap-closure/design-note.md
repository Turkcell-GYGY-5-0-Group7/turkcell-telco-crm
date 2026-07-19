# Sprint 24 Design Note - Decisions and Accepted Precedents

Ratified 2026-07-19 (user-confirmed where noted). This note exists so reviewers read intent, not
accident.

## D1. Addon ordering shape (FR-09, FR-22)

Both shapes ship via one generalized order model:

- `orders.order_type`: NEW_LINE | ADDON | PLAN_CHANGE (derived from items at creation, persisted
  so saga consumers branch without re-deriving).
- `order_items.item_type`: TARIFF | ADDON. ADDON items carry `product_code`,
  `target_subscription_id` (standalone only), and allowance snapshot columns.
- Bundled (NEW_LINE): exactly one TARIFF item + 0..N ADDON items. Activation activates the tariff
  item; one `addon.purchased.v1` per ADDON item is published at fulfillment with the
  subscriptionId from the activation payload.
- Standalone (ADDON): 1..N ADDON items, all with the same required `target_subscription_id`,
  validated at creation (subscription ACTIVE and owned by the customer). No activation leg: on
  `payment.completed.v1` the order confirms and fulfills in one flow.
- Allowance deltas are snapshotted at order creation and carried in the event (immutable event,
  no runtime catalog coupling for usage-service; follows the V5 tariff-snapshot precedent).
- `campaignCode` stays TARIFF-only (campaign eligibility is tariff-scoped, ADR-027).

## D2. Plan change is order-driven and charges the new monthly fee (user-confirmed)

FR-09 frames package change as an order; a direct subscription endpoint would bypass the payment
saga and the PDF's order model. A PLAN_CHANGE order has exactly one item (new `tariffId` +
`target_subscription_id`, tariff must differ from current). The order charges the new tariff's
monthly fee through the existing, unchanged payment saga (payment-service charges order totals
blindly, so refund/compensation work as-is). subscription-service branches on the fetched
orderType at `payment.completed.v1`: NEW_LINE -> activate (unchanged); PLAN_CHANGE ->
`ChangeTariffCommand` publishing `subscription.tariff-changed.v1`; ADDON -> ignore (order-service
owns that leg). Failure of changeTariff reuses the existing activation-failed emitter so the
refund/compensation saga runs with zero new consumers - the event name is a documented reuse, a
future v2 could split it.

## D3. Addon charged at purchase AND invoiced once - accepted precedent, not a double-charge bug

The order saga charges the addon price upfront (like the tariff monthly fee at onboarding), and
the next bill run adds one addon invoice line per purchase (FR-22). This mirrors the existing
tariff precedent: the invoice is the recurring bill of record. A `billed` flag on
`addon_charge_record` guarantees the line appears exactly once.

## D4. Quota semantics

- Addon top-up: `Quota.addAllowance` raises totals AND remaining, clearing
  `thresholdNotified`/`exceededNotified` when the balance is back under the threshold, so a
  subscriber who tops up can be warned again later in the same period.
- Plan change re-provision: current-period quota is reset to the new tariff's allowances with
  `remaining = max(0, newTotal - used)`. Known simplification: in-period addon top-ups are not
  preserved across a plan change (documented, acceptable for MVP).

## D5. Contact info stored plain with log masking (user-confirmed)

The PDF mandates encryption at rest only for TCKN and card data. Email/phone are added as plain
nullable columns with platform `@Sensitive` masking in logs/telemetry (ADR-011). Encrypting them
would cost searchability for zero spec requirement.

## D6. Payment method is a labeled enum; Wallet aggregate is out of scope

`payments.method` (CREDIT_CARD default | BANK_TRANSFER | WALLET) satisfies FR-25's method set
through the existing mock PSP, which ignores the method. The PDF data model's Wallet aggregate
(balance management) is backed by no FR and no acceptance scenario - explicitly out of scope.

## D7. Scope-out confirmations (PDF Section 6.2)

Not built, per the PDF's own scope: MNP (state-machine scaffolding stays a deferred extension
point), prepaid top-up/real-time charging, corporate flows beyond registration (VKN validation IS
wired for CORPORATE registration in 24.5), BTK reports, roaming. Campaign engine and web frontend
were scope-out but already delivered in Sprints 21/16.
