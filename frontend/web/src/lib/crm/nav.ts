// CRM console navigation model (pure, unit tested in Node).
//
// The sidebar renders this list; keeping it as data (not markup) lets the role
// filter and the "wired vs planned" flag be asserted without a DOM. `requiresRoles`
// is a UI convenience only - it hides items a user cannot use - while the routes
// themselves are guarded server-side. `wired` marks whether the page talks to a
// real endpoint or is a planned/coming-soon surface, so the sidebar can badge the
// planned ones honestly.

export interface CrmNavItem {
	href: string;
	label: string;
	/** When set, the item shows only if the user holds one of these realm roles. */
	requiresRoles?: string[];
	/** False for pages with no backend endpoint yet (shown as "Planned"). */
	wired: boolean;
}

export const CRM_NAV: CrmNavItem[] = [
	{ href: '/crm', label: 'Dashboard', wired: true },
	{
		href: '/crm/customers',
		label: 'Customers',
		requiresRoles: ['ADMIN', 'CALL_CENTER_AGENT'],
		wired: true
	},
	{ href: '/crm/subscriptions', label: 'Subscriptions', requiresRoles: ['ADMIN'], wired: true },
	{ href: '/crm/orders', label: 'Orders', requiresRoles: ['ADMIN'], wired: true },
	{ href: '/crm/billing', label: 'Billing', requiresRoles: ['ADMIN'], wired: true },
	{ href: '/crm/tickets', label: 'Tickets', requiresRoles: ['ADMIN'], wired: true },
	{ href: '/crm/catalog', label: 'Catalog', wired: true },
	{ href: '/crm/campaigns', label: 'Campaigns', wired: false },
	{ href: '/crm/reports', label: 'Reports', wired: false }
];

/**
 * The nav items visible to a user with the given roles. An item with no
 * `requiresRoles` is always visible; an item with them shows when the user holds
 * at least one. Order is preserved.
 */
export function visibleNavItems(
	roles: readonly string[],
	items: CrmNavItem[] = CRM_NAV
): CrmNavItem[] {
	return items.filter(
		(item) => !item.requiresRoles || item.requiresRoles.some((role) => roles.includes(role))
	);
}

/**
 * Whether `pathname` should mark `href` active. The dashboard root (`/crm`) matches
 * only itself; every other item also owns its subpaths (so `/crm/customers/123`
 * keeps Customers highlighted).
 */
export function isCurrentCrm(href: string, pathname: string): boolean {
	if (href === '/crm') return pathname === '/crm';
	return pathname === href || pathname.startsWith(`${href}/`);
}
