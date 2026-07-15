// Post-login dashboard shaping for the home route (16.5.3).
//
// Pure and framework-agnostic (unit tested in Node). Reduces the composed
// GET /bff/v1/home payload into the handful of display facts the dashboard
// renders - profile identity, active-subscription count, and the latest
// invoice's paid/overdue tone - so the Svelte page stays a thin adapter and
// this logic is testable without a DOM. Defensive against absent collections
// so a partial composition never renders NaN or throws.

import type { HomeDashboard, InvoiceSummary } from '$lib/api/client';

/** Colour/semantic tone for an invoice status badge. */
export type InvoiceTone = 'paid' | 'overdue' | 'pending' | 'neutral';

/** The dashboard's display-ready view of GET /bff/v1/home. */
export interface HomeSummary {
	/** Customer display name (never a PII identity number). */
	customerName: string;
	/** Customer id, shown as a subtle reference. */
	customerId: string;
	/** Account lifecycle status, e.g. ACTIVE. */
	accountStatus: string;
	/** Number of active subscriptions on the account. */
	activeSubscriptionCount: number;
	/** True when the account has at least one active subscription. */
	hasActiveSubscriptions: boolean;
	/** The latest invoice, or null when the account has none yet. */
	latestInvoice: InvoiceSummary | null;
	/** True when a latest invoice is present. */
	hasLatestInvoice: boolean;
}

/**
 * Map an invoice status to a badge tone, case-insensitively. PAID -> paid,
 * OVERDUE -> overdue, PENDING/ISSUED/UNPAID -> pending; anything else is
 * neutral, so an unknown status still renders a legible (grey) badge.
 */
export function invoiceStatusTone(status: string): InvoiceTone {
	switch ((status ?? '').trim().toUpperCase()) {
		case 'PAID':
			return 'paid';
		case 'OVERDUE':
			return 'overdue';
		case 'PENDING':
		case 'ISSUED':
		case 'UNPAID':
			return 'pending';
		default:
			return 'neutral';
	}
}

/**
 * Reduce the composed home payload to the dashboard's display facts. Tolerates a
 * missing/absent subscription list (treated as none) and a null latest invoice.
 */
export function summarizeHome(home: HomeDashboard): HomeSummary {
	const subscriptions = home.activeSubscriptions ?? [];
	const latestInvoice = home.latestInvoice ?? null;
	return {
		customerName: home.profile.fullName,
		customerId: home.profile.customerId,
		accountStatus: home.profile.status,
		activeSubscriptionCount: subscriptions.length,
		hasActiveSubscriptions: subscriptions.length > 0,
		latestInvoice,
		hasLatestInvoice: latestInvoice !== null
	};
}
