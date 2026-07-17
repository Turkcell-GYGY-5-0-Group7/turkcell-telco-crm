// Route guard for the CRM console group (`/crm/**`). Same client-side, browser-only
// pattern as the subscriber portal's (protected) guard: auth is decided by the
// framework-agnostic route-guard module, and `ssr = false` keeps staff markup from
// being server-rendered before the decision runs.
//
// Beyond "is authenticated", this load also surfaces the caller's roles and linked
// customerId (decoded from the access token) as layout data, so the sidebar can hide
// items the user cannot use and each page can gate its staff-only actions. This is
// presentation only - every CRM endpoint re-checks role and ownership server-side.

import { browser } from '$app/environment';
import { redirect } from '@sveltejs/kit';
import { getActiveUser } from '$lib/auth/oidc';
import { resolveGuard } from '$lib/auth/route-guard';
import { claimsOf } from '$lib/auth/jwt-claims';
import type { LayoutLoad } from './$types';

export const ssr = false;

export const load: LayoutLoad = async ({ url }) => {
	if (!browser) return { roles: [] as string[], customerId: null as string | null };

	const user = await getActiveUser();
	const decision = resolveGuard(user !== null, url.pathname + url.search);
	if (!decision.allow) {
		redirect(302, decision.redirectTo);
	}

	const claims = claimsOf(user?.access_token ?? null);
	return { roles: claims.roles, customerId: claims.customerId };
};
