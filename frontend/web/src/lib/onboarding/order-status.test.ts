import { describe, expect, it, vi } from 'vitest';
import {
	classifyOrderStatus,
	isTerminalOutcome,
	pollOrderStatus,
	type OrderOutcome
} from './order-status';
import type { OrderStatus } from '$lib/api/client';

// Framework-agnostic proof that the wizard's final step is sourced from polling, not an
// optimistic assumption (16.4.2). The statuses asserted here are order-service's REAL
// state machine (OrderStatus: PENDING, CONFIRMED, FULFILLED, CANCELLED, FAILED) - the
// saga drives it entirely server-side once the order is placed. A fake sleep keeps the
// poll loop instant and deterministic; the live saga round-trip is deferred to the
// stack run.

describe('classifyOrderStatus', () => {
	it('treats FULFILLED as the only activated status (case-insensitive)', () => {
		expect(classifyOrderStatus('FULFILLED')).toBe<OrderOutcome>('activated');
		expect(classifyOrderStatus('fulfilled')).toBe<OrderOutcome>('activated');
	});

	it('treats CONFIRMED as still pending: payment completed, subscription not yet active', () => {
		// order-service confirms on payment.completed.v1 and only fulfills on
		// subscription.activated.v1 - stopping at CONFIRMED would report an activation
		// that has not happened (and the saga can still compensate).
		expect(classifyOrderStatus('CONFIRMED')).toBe<OrderOutcome>('pending');
	});

	it('treats CANCELLED and FAILED as terminal failures', () => {
		for (const status of ['CANCELLED', 'cancelled', 'FAILED', 'failed']) {
			expect(classifyOrderStatus(status)).toBe<OrderOutcome>('failed');
		}
	});

	it('treats in-progress/unknown/empty statuses as pending', () => {
		for (const status of ['PENDING', 'whatever', '', null, undefined]) {
			expect(classifyOrderStatus(status)).toBe<OrderOutcome>('pending');
		}
	});

	it('marks only terminal outcomes as terminal', () => {
		expect(isTerminalOutcome('activated')).toBe(true);
		expect(isTerminalOutcome('failed')).toBe(true);
		expect(isTerminalOutcome('pending')).toBe(false);
	});
});

describe('pollOrderStatus', () => {
	const instantSleep = () => Promise.resolve();

	function fetchSequence(statuses: string[]): (id: string) => Promise<OrderStatus> {
		let i = 0;
		return (orderId: string) => {
			const status = statuses[Math.min(i, statuses.length - 1)];
			i += 1;
			return Promise.resolve({ orderId, customerId: 'c-1', status });
		};
	}

	it('follows the real saga path and stops only at FULFILLED', async () => {
		const result = await pollOrderStatus(
			fetchSequence(['PENDING', 'CONFIRMED', 'FULFILLED']),
			'o-1',
			{ sleep: instantSleep }
		);
		expect(result.outcome).toBe('activated');
		expect(result.status).toBe('FULFILLED');
		expect(result.attempts).toBe(3);
		expect(result.timedOut).toBe(false);
	});

	it('reports a failed terminal outcome (compensation)', async () => {
		const result = await pollOrderStatus(fetchSequence(['PENDING', 'CANCELLED']), 'o-2', {
			sleep: instantSleep
		});
		expect(result.outcome).toBe('failed');
		expect(result.timedOut).toBe(false);
	});

	it('carries the order-service customerId out (the only source the wizard has for it)', async () => {
		const result = await pollOrderStatus(
			() => Promise.resolve({ orderId: 'o-7', customerId: 'c-42', status: 'FULFILLED' }),
			'o-7',
			{ sleep: instantSleep }
		);
		expect(result.customerId).toBe('c-42');
	});

	it('times out honestly (pending, not fake success) when the budget is exhausted', async () => {
		const result = await pollOrderStatus(fetchSequence(['PENDING']), 'o-3', {
			sleep: instantSleep,
			maxAttempts: 3
		});
		expect(result.attempts).toBe(3);
		expect(result.timedOut).toBe(true);
		expect(result.outcome).toBe('pending');
	});

	it('sleeps between attempts but not after the last one', async () => {
		const sleep = vi.fn(() => Promise.resolve());
		await pollOrderStatus(fetchSequence(['PENDING']), 'o-4', {
			sleep,
			maxAttempts: 3
		});
		expect(sleep).toHaveBeenCalledTimes(2);
	});

	it('emits an onTick for every fetch with the interim classification', async () => {
		const ticks: { status: string; outcome: OrderOutcome; attempts: number }[] = [];
		await pollOrderStatus(fetchSequence(['PENDING', 'FULFILLED']), 'o-5', {
			sleep: instantSleep,
			onTick: (tick) => ticks.push(tick)
		});
		expect(ticks).toEqual([
			{ status: 'PENDING', outcome: 'pending', attempts: 1 },
			{ status: 'FULFILLED', outcome: 'activated', attempts: 2 }
		]);
	});
});
