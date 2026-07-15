// Route guard for the (protected) group: /onboarding, /account, /invoices
// (subtask 16.3.2). Membership in this route group - not a path match - is what
// makes a route protected; the group name adds no URL segment, so the public
// paths are unchanged.
//
// Auth is client-side (the Keycloak token set lives in sessionStorage via
// oidc-client-ts), so the guard can only run in the BROWSER. This universal load
// is therefore browser-guarded: during SSR/prerender it is a no-op (returns
// early before touching any browser API or session), and the real decision runs
// on hydration and on every client-side navigation into the group. `ssr = false`
// keeps protected markup from being server-rendered before that decision, so an
// unauthenticated visitor never briefly sees guarded content.
//
// The decision itself is delegated to the framework-agnostic, unit-tested
// route-guard module; this file is only the SvelteKit adapter.

import { browser } from '$app/environment';
import { redirect } from '@sveltejs/kit';
import { getActiveUser } from '$lib/auth/oidc';
import { resolveGuard } from '$lib/auth/route-guard';
import type { LayoutLoad } from './$types';

export const ssr = false;

export const load: LayoutLoad = async ({ url }) => {
	if (!browser) return {};

	// Await the session probe so the guard does not race the initial auth load.
	const user = await getActiveUser();
	const decision = resolveGuard(user !== null, url.pathname + url.search);

	if (!decision.allow) {
		// SvelteKit performs a client-side navigation to /login, carrying the
		// originally requested path as ?returnTo= for post-login return.
		redirect(302, decision.redirectTo);
	}

	return {};
};
