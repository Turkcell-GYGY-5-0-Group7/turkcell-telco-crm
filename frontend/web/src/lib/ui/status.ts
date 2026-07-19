// Status -> badge tone mapping for the whole app.
//
// Pure and framework-agnostic (unit tested in Node). Every status string the BFF
// can send is mapped here rather than in a component, so /account and the
// dashboard cannot drift into showing the same status in different colours. An
// unrecognised status is deliberately NEUTRAL, not an error: an unknown lifecycle
// state added server-side must still render legibly rather than red.
//
// Invoice statuses are NOT re-mapped here - $lib/home/summary already owns that
// (InvoiceTone), so this only adapts that verdict onto the badge palette.

import type { InvoiceTone } from '$lib/home/summary';

/** The palettes a Badge can render. */
export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/** Subscription lifecycle -> tone. ACTIVE is good; terminal states are red. */
export function subscriptionTone(status: string): BadgeTone {
	switch ((status ?? '').trim().toUpperCase()) {
		case 'ACTIVE':
			return 'success';
		case 'SUSPENDED':
		case 'PENDING':
			return 'warning';
		case 'TERMINATED':
		case 'CANCELLED':
			return 'danger';
		default:
			return 'neutral';
	}
}

/** Customer lifecycle -> tone. */
export function customerTone(status: string): BadgeTone {
	return (status ?? '').trim().toUpperCase() === 'ACTIVE' ? 'success' : 'neutral';
}

/** Adapt the invoice tone owned by $lib/home/summary onto the badge palette. */
export function invoiceToneToBadge(tone: InvoiceTone): BadgeTone {
	switch (tone) {
		case 'paid':
			return 'success';
		case 'overdue':
			return 'danger';
		case 'pending':
			return 'warning';
		default:
			return 'neutral';
	}
}

/** Order lifecycle -> tone. Terminal-good is green; cancelled/failed are red. */
export function orderTone(status: string): BadgeTone {
	switch ((status ?? '').trim().toUpperCase()) {
		case 'FULFILLED':
		case 'COMPLETED':
		case 'CONFIRMED':
			return 'success';
		case 'PENDING':
		case 'PROCESSING':
			return 'warning';
		case 'CANCELLED':
		case 'FAILED':
			return 'danger';
		default:
			return 'neutral';
	}
}

/** Support-ticket lifecycle -> tone. An open/assigned ticket is in-progress. */
export function ticketTone(status: string): BadgeTone {
	switch ((status ?? '').trim().toUpperCase()) {
		case 'RESOLVED':
		case 'CLOSED':
			return 'success';
		case 'OPEN':
		case 'ASSIGNED':
		case 'IN_PROGRESS':
			return 'warning';
		default:
			return 'neutral';
	}
}

/** Notification delivery status -> tone. */
export function notificationTone(status: string): BadgeTone {
	switch ((status ?? '').trim().toUpperCase()) {
		case 'SENT':
		case 'DELIVERED':
			return 'success';
		case 'PENDING':
		case 'QUEUED':
			return 'warning';
		case 'FAILED':
			return 'danger';
		default:
			return 'neutral';
	}
}

/**
 * Usage gauge fill tone. Consumption is only worth flagging as it approaches the
 * allowance: 80% warns, 95% is effectively spent.
 */
export function gaugeTone(percent: number): 'ok' | 'warning' | 'danger' {
	if (!Number.isFinite(percent)) {
		return 'ok';
	}
	if (percent >= 95) {
		return 'danger';
	}
	if (percent >= 80) {
		return 'warning';
	}
	return 'ok';
}
