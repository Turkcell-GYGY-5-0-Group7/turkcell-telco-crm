import { describe, expect, it } from 'vitest';
import {
	recoveryActionFor,
	recoveryStep,
	shouldReuseCustomer,
	type RecoveryAction
} from './recovery';

// Framework-agnostic proof of the wizard's failure/compensation recovery policy
// (16.4.3): which honest recovery path a terminal failure maps to. What the wizard may
// offer is bounded by the real contracts - the browser cannot retry payment (charging
// is event-driven; POST /api/v1/payments is payment-service's ADMIN-only override), and
// a CANCELLED/FAILED order is terminal in order-service's state machine. So the only
// real recovery is placing a NEW order for the same, already-registered customer.

describe('recoveryActionFor', () => {
	it('offers a new order for the terminal failure statuses order-service can reach', () => {
		for (const status of ['CANCELLED', 'cancelled', 'FAILED', 'failed']) {
			expect(recoveryActionFor(status)).toBe<RecoveryAction>('retry-order');
		}
	});

	it('offers no recovery for a fulfilled or still-pending order', () => {
		for (const status of ['FULFILLED', 'PENDING', 'CONFIRMED', '', null, undefined]) {
			expect(recoveryActionFor(status)).toBe<RecoveryAction>('none');
		}
	});

	it('never offers a payment retry - the browser has no payment call to make', () => {
		const actions = ['CANCELLED', 'FAILED', 'FULFILLED', 'PENDING'].map(recoveryActionFor);
		expect(actions).not.toContain('retry-payment');
	});
});

describe('recoveryStep', () => {
	it('sends a failed order back to the review step to be placed again', () => {
		expect(recoveryStep('retry-order')).toBe('review');
	});

	it('has no step to route to when there is nothing to recover', () => {
		expect(recoveryStep('none')).toBeNull();
	});
});

describe('shouldReuseCustomer', () => {
	it('reuses the existing customer once an id is known (the BFF customerId path)', () => {
		// Re-registering the same TCKN would be rejected by customer-service, so a retry
		// MUST go through the reuse path.
		expect(shouldReuseCustomer('c-1')).toBe(true);
	});

	it('registers a new customer when no id is known yet', () => {
		expect(shouldReuseCustomer('')).toBe(false);
		expect(shouldReuseCustomer('   ')).toBe(false);
		expect(shouldReuseCustomer(null)).toBe(false);
		expect(shouldReuseCustomer(undefined)).toBe(false);
	});
});
