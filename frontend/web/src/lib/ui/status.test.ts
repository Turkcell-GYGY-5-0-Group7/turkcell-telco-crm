import { describe, expect, it } from 'vitest';
import {
	customerTone,
	gaugeTone,
	invoiceToneToBadge,
	notificationTone,
	orderTone,
	subscriptionTone,
	ticketTone
} from './status';

describe('subscriptionTone', () => {
	it('maps the lifecycle states', () => {
		expect(subscriptionTone('ACTIVE')).toBe('success');
		expect(subscriptionTone('SUSPENDED')).toBe('warning');
		expect(subscriptionTone('PENDING')).toBe('warning');
		expect(subscriptionTone('TERMINATED')).toBe('danger');
		expect(subscriptionTone('CANCELLED')).toBe('danger');
	});

	it('is case- and whitespace-insensitive', () => {
		expect(subscriptionTone(' active ')).toBe('success');
		expect(subscriptionTone('Terminated')).toBe('danger');
	});

	it('renders an unknown status neutrally rather than as an error', () => {
		expect(subscriptionTone('MIGRATING')).toBe('neutral');
		expect(subscriptionTone('')).toBe('neutral');
	});
});

describe('customerTone', () => {
	it('greens only an active customer', () => {
		expect(customerTone('ACTIVE')).toBe('success');
		expect(customerTone('active')).toBe('success');
	});

	it('is neutral otherwise', () => {
		expect(customerTone('BLOCKED')).toBe('neutral');
		expect(customerTone('')).toBe('neutral');
	});
});

describe('invoiceToneToBadge', () => {
	it('adapts every invoice tone onto the badge palette', () => {
		expect(invoiceToneToBadge('paid')).toBe('success');
		expect(invoiceToneToBadge('overdue')).toBe('danger');
		expect(invoiceToneToBadge('pending')).toBe('warning');
		expect(invoiceToneToBadge('neutral')).toBe('neutral');
	});
});

describe('orderTone', () => {
	it('greens fulfilled/confirmed, warns pending, reds cancelled/failed', () => {
		expect(orderTone('FULFILLED')).toBe('success');
		expect(orderTone('CONFIRMED')).toBe('success');
		expect(orderTone('PENDING')).toBe('warning');
		expect(orderTone('CANCELLED')).toBe('danger');
		expect(orderTone('FAILED')).toBe('danger');
	});

	it('is neutral for an unknown status', () => {
		expect(orderTone('SOMETHING')).toBe('neutral');
	});
});

describe('ticketTone', () => {
	it('greens resolved, warns open/assigned', () => {
		expect(ticketTone('RESOLVED')).toBe('success');
		expect(ticketTone('OPEN')).toBe('warning');
		expect(ticketTone('ASSIGNED')).toBe('warning');
	});

	it('is neutral for an unknown status', () => {
		expect(ticketTone('PARKED')).toBe('neutral');
	});
});

describe('notificationTone', () => {
	it('maps delivery states', () => {
		expect(notificationTone('SENT')).toBe('success');
		expect(notificationTone('PENDING')).toBe('warning');
		expect(notificationTone('FAILED')).toBe('danger');
		expect(notificationTone('UNKNOWN')).toBe('neutral');
	});
});

describe('gaugeTone', () => {
	it('stays calm below 80%', () => {
		expect(gaugeTone(0)).toBe('ok');
		expect(gaugeTone(79.9)).toBe('ok');
	});

	it('warns from 80% and alarms from 95%', () => {
		expect(gaugeTone(80)).toBe('warning');
		expect(gaugeTone(94.9)).toBe('warning');
		expect(gaugeTone(95)).toBe('danger');
		expect(gaugeTone(100)).toBe('danger');
	});

	it('does not blow up on a non-finite percentage', () => {
		expect(gaugeTone(Number.NaN)).toBe('ok');
	});
});
