// Framework-agnostic route-guard decision logic (subtask 16.3.2).
//
// The guard itself runs in the browser (client-side auth: the token set lives in
// sessionStorage via oidc-client-ts). To keep the DECISION testable without a DOM
// or a live Keycloak, all policy lives here as pure functions and the SvelteKit
// `+layout.ts` guard is a thin adapter that supplies `authenticated` + `pathname`
// and acts on the returned decision.
//
// Two return-to hops cooperate so a user lands back where they started:
//   1. guard -> `/login?returnTo=<intended path>`  (this module builds that URL)
//   2. `/login` -> Keycloak -> callback, carrying the same path in the OIDC
//      `state` (see oidc.ts#login / completeLogin). `safeReturnTo` sanitises the
//      value at both boundaries so it can never become an open redirect.

/** Auth route that must always stay public (the guard redirects here). */
export const LOGIN_ROUTE = '/login';
/** Query parameter carrying the originally requested path to `/login`. */
export const RETURN_TO_PARAM = 'returnTo';

/**
 * Routes protected behind an authenticated Keycloak session. This list mirrors
 * the `(protected)` route-group membership plus the `crm` group; it exists for
 * documentation and unit assertions - runtime protection is enforced by each
 * group's `+layout.ts` guard, not by matching against this array.
 */
export const PROTECTED_ROUTES = [
	'/onboarding',
	'/account',
	'/invoices',
	'/usage',
	'/orders',
	'/tickets',
	'/notifications',
	'/crm'
] as const;

export type GuardDecision = { allow: true } | { allow: false; redirectTo: string };

/** True when `pathname` is one of the guarded routes (or a child of one). */
export function isProtectedPath(pathname: string): boolean {
	return PROTECTED_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

/**
 * Sanitise a post-login return target into a safe, local path. Rejects anything
 * that is not a same-origin absolute path (protocol-relative `//host`, absolute
 * URLs) and never returns an auth route, so restoring it cannot cause an open
 * redirect or a `/login` <-> guard loop. Falls back to `/`.
 */
export function safeReturnTo(raw: string | null | undefined): string {
	if (!raw) return '/';
	// Must be a local absolute path; reject protocol-relative and absolute URLs.
	if (!raw.startsWith('/') || raw.startsWith('//')) return '/';
	// Never bounce back into the auth routes (would loop or dead-end).
	if (
		raw === LOGIN_ROUTE ||
		raw.startsWith(`${LOGIN_ROUTE}/`) ||
		raw.startsWith(`${LOGIN_ROUTE}?`)
	) {
		return '/';
	}
	if (raw === '/auth' || raw.startsWith('/auth/')) return '/';
	return raw;
}

/** Build the `/login` URL that carries `pathname` as the post-login return target. */
export function loginUrlFor(pathname: string): string {
	const target = safeReturnTo(pathname);
	if (target === '/') return LOGIN_ROUTE;
	return `${LOGIN_ROUTE}?${RETURN_TO_PARAM}=${encodeURIComponent(target)}`;
}

/**
 * Decide whether a visit to `pathname` is allowed for the given auth state. An
 * authenticated session passes through; otherwise the caller must redirect to the
 * returned `/login` URL, which preserves the intended path for post-login return.
 */
export function resolveGuard(authenticated: boolean, pathname: string): GuardDecision {
	if (authenticated) return { allow: true };
	return { allow: false, redirectTo: loginUrlFor(pathname) };
}
