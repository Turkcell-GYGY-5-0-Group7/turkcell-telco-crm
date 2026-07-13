import { describe, expect, it, vi } from 'vitest';
import {
	classifyOrderStatus,
	isTerminalOutcome,
	pollOrderStatus,
	type OrderOutcome
} from './order-status';
import type { OrderStatus } from '$lib/api/client';

// Framework-agnostic proof that the wizard's final step is sourced from polling,
// not an optimistic assumption (16.4.2). A fake sleep keeps the poll loop instant
// and deterministic; the live saga round-trip is deferred to the stack run.

describe('classifyOrderStatus', () => {
	it('treats activation/confirmation statuses as activated (case-insensitive)', () => {
		for (const status of ['ACTIVE', 'activated', 'Confirmed', 'COMPLETED']) {
			expect(classifyOrderStatus(status)).toBe<OrderOutcome>('activated');
		}
	});

	it('treats compensation/cancellation statuses as failed', () => {
		for (const status of [
			'CANCELLED',
			'canceled',
			'FAILED',
			'COMPENSATED',
			'REJECTED',
			'KYC_REJECTED',
			'KYC_FAILED'
		]) {
			expect(classifyOrderStatus(status)).toBe<OrderOutcome>('failed');
		}
	});

	it('treats in-progress/unknown/empty statuses as pending', () => {
		for (const status of ['PENDING_PAYMENT', 'PROCESSING', 'whatever', '', null, undefined]) {
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
			return Promise.resolve({ orderId, status });
		};
	}

	it('stops as soon as a terminal (activated) status is observed', async () => {
		const result = await pollOrderStatus(
			fetchSequence(['PENDING_PAYMENT', 'PENDING_PAYMENT', 'ACTIVE']),
			'o-1',
			{ sleep: instantSleep }
		);
		expect(result.outcome).toBe('activated');
		expect(result.status).toBe('ACTIVE');
		expect(result.attempts).toBe(3);
		expect(result.timedOut).toBe(false);
	});

	it('reports a failed terminal outcome (compensation)', async () => {
		const result = await pollOrderStatus(fetchSequence(['PENDING_PAYMENT', 'CANCELLED']), 'o-2', {
			sleep: instantSleep
		});
		expect(result.outcome).toBe('failed');
		expect(result.timedOut).toBe(false);
	});

	it('times out honestly (pending, not fake success) when the budget is exhausted', async () => {
		const result = await pollOrderStatus(fetchSequence(['PENDING_PAYMENT']), 'o-3', {
			sleep: instantSleep,
			maxAttempts: 3
		});
		expect(result.attempts).toBe(3);
		expect(result.timedOut).toBe(true);
		expect(result.outcome).toBe('pending');
	});

	it('sleeps between attempts but not after the last one', async () => {
		const sleep = vi.fn(() => Promise.resolve());
		await pollOrderStatus(fetchSequence(['PENDING_PAYMENT']), 'o-4', {
			sleep,
			maxAttempts: 3
		});
		expect(sleep).toHaveBeenCalledTimes(2);
	});

	it('emits an onTick for every fetch with the interim classification', async () => {
		const ticks: { status: string; outcome: OrderOutcome; attempts: number }[] = [];
		await pollOrderStatus(fetchSequence(['PENDING_PAYMENT', 'ACTIVE']), 'o-5', {
			sleep: instantSleep,
			onTick: (tick) => ticks.push(tick)
		});
		expect(ticks).toEqual([
			{ status: 'PENDING_PAYMENT', outcome: 'pending', attempts: 1 },
			{ status: 'ACTIVE', outcome: 'activated', attempts: 2 }
		]);
	});
});
