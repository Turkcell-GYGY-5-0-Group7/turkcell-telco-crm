// Framework-agnostic order/saga status classification and polling (16.4.2).
//
// The onboarding saga (order-service, ADR-009) is eventually consistent: after
// payment the order transitions through the saga until it either activates the
// subscription or compensates (refund + order CANCELLED). The wizard's final
// step must reflect the TRUE terminal state sourced from polling, never an
// optimistic "payment succeeded => activated" assumption. This module owns the
// classification and the poll loop as pure/injectable logic so both are unit
// testable in Node with a fake clock - no real timers, DOM, or backend.

import type { OrderStatus } from '$lib/api/client';

/** Terminal or in-progress interpretation of a raw order status string. */
export type OrderOutcome = 'pending' | 'activated' | 'failed';

/**
 * Statuses that mean the onboarding saga finished successfully and the
 * subscription is live (order-service / subscription-service semantics).
 */
const ACTIVATED_STATUSES = new Set(['ACTIVE', 'ACTIVATED', 'CONFIRMED', 'COMPLETED']);

/**
 * Statuses that mean the saga terminated unsuccessfully and compensation ran
 * (payment failure -> refund -> order CANCELLED, or KYC rejection -> REJECTED).
 * Includes the KYC-specific rejection variants so an identity rejection is a
 * recognised terminal failure the wizard can offer a corrective path for (16.4.3).
 */
const FAILED_STATUSES = new Set([
	'CANCELLED',
	'CANCELED',
	'FAILED',
	'COMPENSATED',
	'REJECTED',
	'KYC_REJECTED',
	'KYC_FAILED'
]);

/**
 * Map a raw order status to a terminal/in-progress outcome. Unknown or
 * in-progress statuses (e.g. PENDING_PAYMENT, PROCESSING) are treated as
 * `pending` so polling continues rather than declaring a premature result.
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
	for (let attempt = 1; attempt <= maxAttempts; attempt++) {
		const current = await fetchStatus(orderId);
		lastStatus = current.status;
		const outcome = classifyOrderStatus(current.status);
		options.onTick?.({ status: current.status, outcome, attempts: attempt });

		if (isTerminalOutcome(outcome)) {
			return { orderId, status: current.status, outcome, attempts: attempt, timedOut: false };
		}
		if (attempt < maxAttempts) {
			await sleep(delayMs);
		}
	}

	return {
		orderId,
		status: lastStatus,
		outcome: classifyOrderStatus(lastStatus),
		attempts: maxAttempts,
		timedOut: true
	};
}
