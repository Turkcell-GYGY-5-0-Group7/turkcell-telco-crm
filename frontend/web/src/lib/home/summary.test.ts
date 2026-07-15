import { describe, expect, it } from 'vitest';
import type { HomeDashboard } from '$lib/api/client';
import { invoiceStatusTone, summarizeHome } from './summary';

const invoice = {
	invoiceId: 'inv-1',
	period: '2026-06',
	amount: 149.9,
	currency: 'TRY',
	status: 'PAID',
	pdfUrl: '/api/v1/invoices/inv-1/pdf'
};

function home(overrides: Partial<HomeDashboard> = {}): HomeDashboard {
	return {
		profile: { customerId: 'cust-1', fullName: 'Ada Lovelace', status: 'ACTIVE' },
		activeSubscriptions: [
			{ subscriptionId: 'sub-1', msisdn: '+905551112233', tariffCode: 'GOLD', status: 'ACTIVE' }
		],
		latestInvoice: invoice,
		...overrides
	};
}

describe('summarizeHome', () => {
	it('projects profile identity and status', () => {
		const summary = summarizeHome(home());
		expect(summary.customerName).toBe('Ada Lovelace');
		expect(summary.customerId).toBe('cust-1');
		expect(summary.accountStatus).toBe('ACTIVE');
	});

	it('counts active subscriptions', () => {
		const summary = summarizeHome(
			home({
				activeSubscriptions: [
					{ subscriptionId: 's1', msisdn: '+900000000001', tariffCode: 'A', status: 'ACTIVE' },
					{ subscriptionId: 's2', msisdn: '+900000000002', tariffCode: 'B', status: 'ACTIVE' }
				]
			})
		);
		expect(summary.activeSubscriptionCount).toBe(2);
		expect(summary.hasActiveSubscriptions).toBe(true);
	});

	it('reports no subscriptions honestly', () => {
		const summary = summarizeHome(home({ activeSubscriptions: [] }));
		expect(summary.activeSubscriptionCount).toBe(0);
		expect(summary.hasActiveSubscriptions).toBe(false);
	});

	it('tolerates an absent subscription collection', () => {
		const summary = summarizeHome(
			home({ activeSubscriptions: undefined as unknown as HomeDashboard['activeSubscriptions'] })
		);
		expect(summary.activeSubscriptionCount).toBe(0);
		expect(summary.hasActiveSubscriptions).toBe(false);
	});

	it('surfaces the latest invoice when present', () => {
		const summary = summarizeHome(home());
		expect(summary.hasLatestInvoice).toBe(true);
		expect(summary.latestInvoice?.invoiceId).toBe('inv-1');
	});

	it('reports a missing latest invoice honestly', () => {
		const summary = summarizeHome(home({ latestInvoice: null }));
		expect(summary.hasLatestInvoice).toBe(false);
		expect(summary.latestInvoice).toBeNull();
	});
});

describe('invoiceStatusTone', () => {
	it('maps known statuses case-insensitively', () => {
		expect(invoiceStatusTone('PAID')).toBe('paid');
		expect(invoiceStatusTone('paid')).toBe('paid');
		expect(invoiceStatusTone('Overdue')).toBe('overdue');
		expect(invoiceStatusTone('PENDING')).toBe('pending');
		expect(invoiceStatusTone('issued')).toBe('pending');
		expect(invoiceStatusTone('UNPAID')).toBe('pending');
	});

	it('falls back to a neutral tone for unknown or blank statuses', () => {
		expect(invoiceStatusTone('SOMETHING')).toBe('neutral');
		expect(invoiceStatusTone('')).toBe('neutral');
	});
});
