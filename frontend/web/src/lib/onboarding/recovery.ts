// Framework-agnostic failure/compensation recovery policy for the onboarding
// wizard (subtask 16.4.3).
//
// 16.4.2 established that the wizard's final step reflects the TRUE saga outcome
// sourced from polling (activated / failed / still-pending). This module turns a
// failure into an ACTIONABLE next step.
//
// What a failure can honestly offer is bounded by the real contracts:
//   - The browser CANNOT retry payment. Charging is event-driven off
//     `order.created.v1` (TELCO-CRM-MVP Section 9.2) and `POST /api/v1/payments` is
//     payment-service's ADMIN-only manual override. A "retry payment" button would
//     be a 403 for a subscriber, and for an admin a SECOND charge on the same order.
//   - A failed order is terminal in order-service's state machine (CANCELLED /
//     FAILED). It cannot be revived; compensation already refunded the charge.
// So the only real recovery is to PLACE A NEW ORDER for the same customer. The
// customer (and their KYC document) already exist, and customer-service would reject
// a duplicate TCKN, so the new order MUST use the BFF's `customerId` reuse path with
// a fresh Idempotency-Key. Everything here is pure/injectable and unit tested; the
// SvelteKit page is a thin adapter that routes the step and calls the BFF client.

import type { WizardStep } from './wizard';
import { classifyOrderStatus } from './order-status';

/**
 * The recovery path offered for a terminal failure:
 * - `retry-order`: the saga failed and compensated (charge refunded, order CANCELLED/
 *   FAILED). The user may place a NEW order for the same, already-registered customer
 *   (reuse path, fresh idempotency key) or start over.
 * - `none`: not a failure (activated or still pending) - no recovery needed.
 */
export type RecoveryAction = 'retry-order' | 'none';

/**
 * Map a terminal order/saga status to the recovery path the wizard should offer.
 * Only `failed` outcomes (per {@link classifyOrderStatus}: CANCELLED / FAILED) yield
 * an actionable recovery; `activated` (FULFILLED) and `pending` (PENDING / CONFIRMED)
 * yield `none`.
 */
export function recoveryActionFor(status: string | null | undefined): RecoveryAction {
	return classifyOrderStatus(status) === 'failed' ? 'retry-order' : 'none';
}

/**
 * The wizard step a recovery action routes the user to, or `null` when there is
 * nothing to correct. A failed order sends the user back to the `review` step, where
 * the plan can be checked and the order placed again.
 */
export function recoveryStep(action: RecoveryAction): WizardStep | null {
	return action === 'retry-order' ? 'review' : null;
}

/**
 * Whether the wizard should re-place the order through the BFF's REUSE path
 * (`customerId`, no register/KYC) rather than the REGISTER path. True once a customer
 * id is known - i.e. after an order was placed and polled at least once, which is
 * exactly the retry case. Re-registering would hit customer-service's duplicate-TCKN
 * rejection, so this decision is the difference between a working retry and a certain
 * 409.
 */
export function shouldReuseCustomer(customerId: string | null | undefined): boolean {
	return typeof customerId === 'string' && customerId.trim().length > 0;
}
