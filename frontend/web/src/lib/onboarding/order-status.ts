// Framework-agnostic order/saga status classification and polling (16.4.2).
//
// The onboarding saga (order-service, ADR-009 / TELCO-CRM-MVP Section 9.2) is
// eventually consistent and entirely event-driven once the order is placed:
// order.created.v1 -> payment-service charges -> payment.completed.v1 -> order
// CONFIRMED -> subscription-service activates -> subscription.activated.v1 ->
// order FULFILLED. Compensation on failure refunds and moves the order to
// CANCELLED/FAILED. Polling the order is therefore the ONLY honest source of the
// wizard's final state - the browser performs no payment call to infer it from.
// This module owns the classification and the poll loop as pure/injectable logic
// so both are unit testable in Node with a fake clock - no timers, DOM, or backend.

import type { OrderStatus } from '$lib/api/client';

/** Terminal or in-progress interpretation of a raw order status string. */
export type OrderOutcome = 'pending' | 'activated' | 'failed';

/**
 * The ONLY status that means the saga finished successfully and the subscription is
 * live. order-service's state machine (`OrderStatus`: PENDING, CONFIRMED, FULFILLED,
 * CANCELLED, FAILED) reaches FULFILLED when it consumes `subscription.activated.v1`.
 * CONFIRMED is deliberately NOT here: it only means payment completed, with the
 * subscription not yet activated (and the saga still able to compensate), so treating
 * it as success would declare activation that has not happened.
 */
const ACTIVATED_STATUSES = new Set(['FULFILLED']);

/**
 * Statuses that mean the saga terminated unsuccessfully: FAILED (payment failure) and
 * CANCELLED (compensation - refund, then the order is cancelled - or a customer
 * cancellation). Both are terminal in order-service's state machine.
 */
const FAILED_STATUSES = new Set(['CANCELLED', 'FAILED']);

/**
 * Map a raw order status to a terminal/in-progress outcome. In-progress statuses
 * (PENDING, CONFIRMED) and any unknown status are treated as `pending` so polling
 * continues rather than declaring a premature result.
 */
export function classifyOrderStatus(status: string | null | undefined): OrderOutcome {
	const normalized = (status ?? '').trim().toUpperCase();
	if (ACTIVATED_STATUSES.has(normalized)) return 'activated';
	if (FAILED_STATUSES.has(normalized)) return 'failed';
	return 'pending';
}

export function isTerminalOutcome(outcome: OrderOutcome): boolean {
	return outcome === 'activated' || outcome === 'failed';
}

/** Outcome of a completed poll run. */
export interface PollResult {
	orderId: string;
	/** The last status observed. */
	status: string;
	outcome: OrderOutcome;
	/**
	 * The customer the order belongs to, as reported by order-service. This is the
	 * wizard's only real source for the id of the customer the BFF registered (the
	 * order response carries none), and it feeds the BFF's `customerId` reuse path
	 * when an order has to be placed again. Empty only if polling never succeeded.
	 */
	customerId: string;
	/** Number of status fetches performed (>= 1). */
	attempts: number;
	/** True when the attempt budget was exhausted before a terminal outcome. */
	timedOut: boolean;
}

export interface PollOptions {
	/** Maximum status fetches before giving up. Default 20. */
	maxAttempts?: number;
	/** Delay between fetches, in ms. Default 1500. */
	delayMs?: number;
	/** Injectable delay (tests pass an instant/fake sleep). Default setTimeout. */
	sleep?: (ms: number) => Promise<void>;
	/** Called after each fetch with the interim state (drives the live UI). */
	onTick?: (tick: { status: string; outcome: OrderOutcome; attempts: number }) => void;
}

const DEFAULT_MAX_ATTEMPTS = 20;
const DEFAULT_DELAY_MS = 1500;
const defaultSleep = (ms: number): Promise<void> =>
	new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Poll `fetchStatus(orderId)` until it reaches a terminal outcome or the attempt
 * budget is exhausted. Resolves with the last observed state; `timedOut` is true
 * when polling stopped while still `pending`. The caller decides how to present
 * a timeout (the wizard shows an honest "still processing" message, not a fake
 * success). Delays run between attempts only, never after the last one.
 */
export async function pollOrderStatus(
	fetchStatus: (orderId: string) => Promise<OrderStatus>,
	orderId: string,
	options: PollOptions = {}
): Promise<PollResult> {
	const maxAttempts = options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS;
	const delayMs = options.delayMs ?? DEFAULT_DELAY_MS;
	const sleep = options.sleep ?? defaultSleep;

	let lastStatus = '';
	let lastCustomerId = '';
	for (let attempt = 1; attempt <= maxAttempts; attempt++) {
		const current = await fetchStatus(orderId);
		lastStatus = current.status;
		lastCustomerId = current.customerId ?? '';
		const outcome = classifyOrderStatus(current.status);
		options.onTick?.({ status: current.status, outcome, attempts: attempt });

		if (isTerminalOutcome(outcome)) {
			return {
				orderId,
				status: current.status,
				outcome,
				customerId: lastCustomerId,
				attempts: attempt,
				timedOut: false
			};
		}
		if (attempt < maxAttempts) {
			await sleep(delayMs);
		}
	}

	return {
		orderId,
		status: lastStatus,
		outcome: classifyOrderStatus(lastStatus),
		customerId: lastCustomerId,
		attempts: maxAttempts,
		timedOut: true
	};
}
