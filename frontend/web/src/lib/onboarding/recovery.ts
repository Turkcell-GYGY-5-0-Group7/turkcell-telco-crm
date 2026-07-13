// Framework-agnostic failure/compensation recovery policy for the onboarding
// wizard (subtask 16.4.3).
//
// 16.4.2 established that the wizard's final step reflects the TRUE saga outcome
// sourced from polling (activated / failed / still-pending), and that a failure
// is surfaced honestly rather than as a fake success. What it did NOT do is turn
// a failure into an ACTIONABLE next step. That is this module: given the
// terminal order/saga status (or a payment rejection), it decides which honest
// recovery path the user is offered, and it builds the fresh payment attempt a
// retry requires. All of it is pure/injectable so it unit tests in Node with no
// DOM, timers, or backend - the SvelteKit page is a thin adapter that routes the
// step and calls the BFF client per this policy.

import type { WizardStep } from './wizard';
import { classifyOrderStatus } from './order-status';

/**
 * The recovery path offered for a terminal failure:
 * - `retry-payment`: payment failed and the saga compensated (refund + order
 *   CANCELLED). The user may retry payment on the SAME order (with a fresh
 *   idempotency key) or start over.
 * - `restart-kyc`: identity verification was rejected. The user is routed back
 *   to the KYC step to re-upload a document rather than hitting a dead end.
 * - `none`: not a failure (activated or still pending) - no recovery needed.
 */
export type RecoveryAction = 'retry-payment' | 'restart-kyc' | 'none';

/**
 * Statuses that mean identity verification was rejected (customer-service KYC
 * state machine PENDING -> REJECTED, `customer.kyc-rejected.v1`), so the saga
 * fails on the customer's KYC rather than on payment. Matched case-insensitively;
 * any status containing "KYC" is also treated as a KYC rejection.
 */
const KYC_REJECTION_STATUSES = new Set(['REJECTED', 'KYC_REJECTED', 'KYC_FAILED']);

/**
 * Map a terminal order/saga status to the recovery path the wizard should offer.
 * Only `failed` outcomes (per {@link classifyOrderStatus}) yield an actionable
 * recovery; `activated`/`pending` yield `none`. A KYC rejection routes the user
 * back to re-verify identity; every other failure (payment compensation, order
 * cancellation) offers a payment retry.
 */
export function recoveryActionFor(status: string | null | undefined): RecoveryAction {
	if (classifyOrderStatus(status) !== 'failed') return 'none';
	const normalized = (status ?? '').trim().toUpperCase();
	if (KYC_REJECTION_STATUSES.has(normalized) || normalized.includes('KYC')) {
		return 'restart-kyc';
	}
	return 'retry-payment';
}

/**
 * The wizard step a recovery action routes the user to, or `null` when there is
 * nothing to correct. A KYC rejection sends the user back to the `kyc` step
 * (corrective re-upload); a payment failure sends them back to the `payment`
 * step to retry the charge on the same order.
 */
export function recoveryStep(action: RecoveryAction): WizardStep | null {
	switch (action) {
		case 'restart-kyc':
			return 'kyc';
		case 'retry-payment':
			return 'payment';
		default:
			return null;
	}
}

/** A single payment attempt: the mandatory `paymentRequestId` is its idempotency key. */
export interface PaymentAttempt {
	orderId: string;
	customerId: string;
	amount: number;
	/** Idempotency key for this attempt; a retry MUST carry a fresh one. */
	paymentRequestId: string;
}

/**
 * Build a payment attempt for an order with a freshly generated idempotency key.
 * Used for BOTH the first charge and every retry, so a retry is guaranteed a NEW
 * `paymentRequestId` - replaying the previous (failed) key would only return the
 * original failed result from payment-service's idempotency store. The order and
 * customer are carried over unchanged so the retry targets the same order. The id
 * generator is injectable for deterministic tests; it defaults to `crypto.randomUUID`
 * (invoked lazily inside this call, never at module load, to stay off the SSR path).
 */
export function buildPaymentAttempt(
	order: { orderId: string; customerId: string },
	amount: number,
	generateId: () => string = () => globalThis.crypto.randomUUID()
): PaymentAttempt {
	return {
		orderId: order.orderId,
		customerId: order.customerId,
		amount,
		paymentRequestId: generateId()
	};
}
