import { describe, expect, it } from 'vitest';
import {
	buildPaymentAttempt,
	recoveryActionFor,
	recoveryStep,
	type RecoveryAction
} from './recovery';

// Framework-agnostic proof of the wizard's failure/compensation recovery policy
// (16.4.3): which honest recovery path a terminal failure maps to, that a retry
// gets a fresh idempotency key, and that a KYC rejection routes to a corrective
// step. The live saga round-trip is deferred to the stack run (Sprint 15 precedent).

describe('recoveryActionFor', () => {
	it('offers a payment retry for compensation/cancellation failures (case-insensitive)', () => {
		for (const status of ['CANCELLED', 'canceled', 'COMPENSATED', 'FAILED']) {
			expect(recoveryActionFor(status)).toBe<RecoveryAction>('retry-payment');
		}
	});

	it('routes to KYC re-verification for a rejection', () => {
		for (const status of ['REJECTED', 'kyc_rejected', 'KYC_FAILED']) {
			expect(recoveryActionFor(status)).toBe<RecoveryAction>('restart-kyc');
		}
	});

	it('offers no recovery for activated or still-pending outcomes', () => {
		for (const status of [
			'ACTIVE',
			'CONFIRMED',
			'PENDING_PAYMENT',
			'PROCESSING',
			'',
			null,
			undefined
		]) {
			expect(recoveryActionFor(status)).toBe<RecoveryAction>('none');
		}
	});
});

describe('recoveryStep', () => {
	it('sends a KYC rejection back to the corrective KYC step, not a dead end', () => {
		expect(recoveryStep('restart-kyc')).toBe('kyc');
	});

	it('sends a payment failure back to the payment step to retry', () => {
		expect(recoveryStep('retry-payment')).toBe('payment');
	});

	it('has no step to route to when there is nothing to recover', () => {
		expect(recoveryStep('none')).toBeNull();
	});
});

describe('buildPaymentAttempt', () => {
	const order = { orderId: 'o-1', customerId: 'c-1' };

	it('carries the order/customer/amount through unchanged', () => {
		const attempt = buildPaymentAttempt(order, 149.9, () => 'id-1');
		expect(attempt).toEqual({
			orderId: 'o-1',
			customerId: 'c-1',
			amount: 149.9,
			paymentRequestId: 'id-1'
		});
	});

	it('generates a FRESH idempotency key on every attempt (so a retry never replays the failed key)', () => {
		let n = 0;
		const generate = () => `id-${++n}`;
		const first = buildPaymentAttempt(order, 100, generate);
		const retry = buildPaymentAttempt(order, 100, generate);
		expect(first.paymentRequestId).toBe('id-1');
		expect(retry.paymentRequestId).toBe('id-2');
		expect(retry.paymentRequestId).not.toBe(first.paymentRequestId);
		// The retry still targets the same order.
		expect(retry.orderId).toBe(first.orderId);
		expect(retry.customerId).toBe(first.customerId);
	});
});
