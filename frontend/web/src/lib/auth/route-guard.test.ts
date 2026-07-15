import { describe, expect, it } from 'vitest';
import {
	LOGIN_ROUTE,
	PROTECTED_ROUTES,
	isProtectedPath,
	loginUrlFor,
	resolveGuard,
	safeReturnTo
} from './route-guard';

// Framework-agnostic proof of the 16.3.2 guard DECISION: an unauthenticated state
// yields a redirect to /login carrying the intended return path, an authenticated
// state passes through, and the return target can never become an open redirect.
// The live click-through round-trip needs a running Keycloak (deferred to the
// stack run, Sprint 15 precedent).

describe('route-guard decision (16.3.2)', () => {
	it('blocks an unauthenticated visit and redirects to /login carrying the return path', () => {
		const decision = resolveGuard(false, '/account');
		expect(decision.allow).toBe(false);
		if (decision.allow) throw new Error('expected a redirect');
		expect(decision.redirectTo).toBe('/login?returnTo=%2Faccount');
	});

	it('preserves the full requested path (with query) as the return target', () => {
		const decision = resolveGuard(false, '/invoices?page=2');
		expect(decision.allow).toBe(false);
		if (decision.allow) throw new Error('expected a redirect');
		expect(decision.redirectTo).toBe('/login?returnTo=%2Finvoices%3Fpage%3D2');
	});

	it('allows an authenticated visit through with no redirect', () => {
		expect(resolveGuard(true, '/account')).toEqual({ allow: true });
		expect(resolveGuard(true, '/onboarding')).toEqual({ allow: true });
		expect(resolveGuard(true, '/invoices')).toEqual({ allow: true });
	});
});

describe('isProtectedPath', () => {
	it('recognises each protected route and their children', () => {
		for (const route of PROTECTED_ROUTES) {
			expect(isProtectedPath(route)).toBe(true);
			expect(isProtectedPath(`${route}/detail`)).toBe(true);
		}
	});

	it('treats public routes as unprotected', () => {
		expect(isProtectedPath('/')).toBe(false);
		expect(isProtectedPath('/login')).toBe(false);
		expect(isProtectedPath('/auth/callback')).toBe(false);
	});
});

describe('safeReturnTo', () => {
	it('returns a local absolute path unchanged (including query)', () => {
		expect(safeReturnTo('/account')).toBe('/account');
		expect(safeReturnTo('/invoices?page=2')).toBe('/invoices?page=2');
	});

	it('falls back to / for empty or missing input', () => {
		expect(safeReturnTo(undefined)).toBe('/');
		expect(safeReturnTo(null)).toBe('/');
		expect(safeReturnTo('')).toBe('/');
	});

	it('rejects protocol-relative and absolute URLs (open-redirect guard)', () => {
		expect(safeReturnTo('//evil.example')).toBe('/');
		expect(safeReturnTo('http://evil.example/account')).toBe('/');
		expect(safeReturnTo('https://evil.example')).toBe('/');
		expect(safeReturnTo('relative/path')).toBe('/');
	});

	it('never returns an auth route (loop guard)', () => {
		expect(safeReturnTo(LOGIN_ROUTE)).toBe('/');
		expect(safeReturnTo('/login?returnTo=%2Faccount')).toBe('/');
		expect(safeReturnTo('/auth/callback')).toBe('/');
	});
});

describe('loginUrlFor', () => {
	it('encodes the intended path into the returnTo query', () => {
		expect(loginUrlFor('/account')).toBe('/login?returnTo=%2Faccount');
	});

	it('omits returnTo when the target sanitises to /', () => {
		expect(loginUrlFor('/login')).toBe('/login');
		expect(loginUrlFor('//evil.example')).toBe('/login');
	});
});
