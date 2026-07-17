import { describe, expect, it } from 'vitest';
import { CRM_NAV, isCurrentCrm, visibleNavItems, type CrmNavItem } from './nav';

describe('visibleNavItems', () => {
	it('always shows role-free items', () => {
		const labels = visibleNavItems([]).map((i) => i.label);
		expect(labels).toContain('Dashboard');
		expect(labels).toContain('Catalog');
		expect(labels).toContain('Campaigns');
	});

	it('hides role-gated items from a user without the role', () => {
		const labels = visibleNavItems(['MARKETING_MANAGER']).map((i) => i.label);
		expect(labels).not.toContain('Subscriptions');
		expect(labels).not.toContain('Customers');
	});

	it('shows Customers to a call-centre agent and Subscriptions only to admin', () => {
		const agent = visibleNavItems(['CALL_CENTER_AGENT']).map((i) => i.label);
		expect(agent).toContain('Customers');
		expect(agent).not.toContain('Subscriptions');

		const admin = visibleNavItems(['ADMIN']).map((i) => i.label);
		expect(admin).toContain('Subscriptions');
		expect(admin).toContain('Customers');
	});

	it('preserves order', () => {
		const items: CrmNavItem[] = [
			{ href: '/a', label: 'A', wired: true },
			{ href: '/b', label: 'B', requiresRoles: ['ADMIN'], wired: true },
			{ href: '/c', label: 'C', wired: true }
		];
		expect(visibleNavItems(['ADMIN'], items).map((i) => i.label)).toEqual(['A', 'B', 'C']);
	});

	it('marks the planned items unwired', () => {
		const planned = CRM_NAV.filter((i) => !i.wired).map((i) => i.label);
		expect(planned).toEqual(['Campaigns', 'Reports']);
	});
});

describe('isCurrentCrm', () => {
	it('matches the dashboard root only exactly', () => {
		expect(isCurrentCrm('/crm', '/crm')).toBe(true);
		expect(isCurrentCrm('/crm', '/crm/customers')).toBe(false);
	});

	it('matches a section and its children', () => {
		expect(isCurrentCrm('/crm/customers', '/crm/customers')).toBe(true);
		expect(isCurrentCrm('/crm/customers', '/crm/customers/abc')).toBe(true);
		expect(isCurrentCrm('/crm/customers', '/crm/orders')).toBe(false);
	});
});
