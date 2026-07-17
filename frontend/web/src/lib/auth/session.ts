// Convenience readers over the current access token, for pages that need the
// caller's own linked customerId or roles to build a self-scoped gateway request.
//
// These read the in-memory token (token-store) through the pure jwt-claims decoder.
// They are browser-time helpers - during SSR the token store is empty and they
// return the same "no session" answer a signed-out user would. As everywhere, this
// only decides what the UI asks for; the server re-checks the real identity.

import { getAccessToken } from './token-store';
import { claimsOf, isAdmin, isStaff, type SessionClaims } from './jwt-claims';

/** The current session's decoded claims (empty when signed out / on the server). */
export function currentClaims(): SessionClaims {
	return claimsOf(getAccessToken());
}

/** The caller's linked customerId, or null when unlinked / signed out. */
export function currentCustomerId(): string | null {
	return currentClaims().customerId;
}

/** Whether the current session holds the ADMIN role. */
export function currentIsAdmin(): boolean {
	return isAdmin(currentClaims());
}

/** Whether the current session holds any staff (non-subscriber) role. */
export function currentIsStaff(): boolean {
	return isStaff(currentClaims());
}
