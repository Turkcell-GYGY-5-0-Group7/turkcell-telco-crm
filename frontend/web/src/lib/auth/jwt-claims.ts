// Client-side reader for the display/gating claims in the Keycloak access token.
//
// The gateway is the ONLY authority on authorization: it validates the JWT
// signature against the realm JWKS and every service re-checks ownership/roles
// server-side. This module never verifies anything - it only decodes the token
// PAYLOAD so the UI can decide what to SHOW (which nav to render, whether to
// offer a staff-only action) without firing a request that is guaranteed to 403.
// A tampered token buys nothing: the request still fails at the gateway.
//
// Pure and framework-agnostic (unit tested in Node): decoding is base64url +
// JSON with no browser or oidc-client-ts dependency, so the whole policy is
// testable without a live Keycloak. The token itself is sourced elsewhere
// (`$lib/auth/token-store`); this module is handed a string.

/** Realm roles that grant access to the staff-facing CRM console. */
export const STAFF_ROLES = [
	'CALL_CENTER_AGENT',
	'DEALER',
	'MARKETING_MANAGER',
	'BILLING_OPERATOR',
	'ADMIN'
] as const;

/** The subset of token claims the UI reads. */
export interface SessionClaims {
	/** Keycloak subject (stable user id). */
	sub: string | null;
	/** Linked business customer id, or null until onboarding links the identity. */
	customerId: string | null;
	/** Flat realm-role list (the `roles` claim via the `telco-roles` client scope). */
	roles: string[];
	/** Preferred display name, when present. */
	preferredUsername: string | null;
}

const EMPTY_CLAIMS: SessionClaims = {
	sub: null,
	customerId: null,
	roles: [],
	preferredUsername: null
};

/**
 * Decode a JWT's payload segment into a plain object, WITHOUT verifying the
 * signature (see the module header: verification is the gateway's job). Returns
 * null for anything that is not a well-formed three-segment JWT with a JSON
 * object payload - a malformed or absent token simply yields "no claims".
 */
export function decodeJwtPayload(token: string | null | undefined): Record<string, unknown> | null {
	if (!token) return null;
	const segments = token.split('.');
	if (segments.length !== 3) return null;

	const json = base64UrlDecode(segments[1]);
	if (json === null) return null;

	try {
		const parsed: unknown = JSON.parse(json);
		return typeof parsed === 'object' && parsed !== null
			? (parsed as Record<string, unknown>)
			: null;
	} catch {
		return null;
	}
}

/**
 * Read the UI-relevant claims from a token. Missing or malformed claims degrade
 * to safe defaults (no roles, null ids), so a caller never has to null-check the
 * token shape - an unrecognised token behaves exactly like an anonymous one.
 */
export function claimsOf(token: string | null | undefined): SessionClaims {
	const payload = decodeJwtPayload(token);
	if (!payload) return EMPTY_CLAIMS;
	return {
		sub: asString(payload.sub),
		customerId: asString(payload.customerId),
		roles: asStringArray(payload.roles),
		preferredUsername: asString(payload.preferred_username)
	};
}

/** True when the claims carry at least one of the given realm roles. */
export function hasAnyRole(claims: SessionClaims, roles: readonly string[]): boolean {
	return roles.some((role) => claims.roles.includes(role));
}

/** True when the session holds any staff (non-subscriber) role -> CRM console. */
export function isStaff(claims: SessionClaims): boolean {
	return hasAnyRole(claims, STAFF_ROLES);
}

/** True when the session holds the ADMIN role. */
export function isAdmin(claims: SessionClaims): boolean {
	return claims.roles.includes('ADMIN');
}

// -- internals --------------------------------------------------------------

/**
 * Decode a base64url segment to a UTF-8 string. Works identically in the browser
 * and in Node (both expose `atob`, `Uint8Array`, and `TextDecoder`), so the same
 * code path is exercised by the unit tests and at runtime. Returns null on any
 * decode error rather than throwing.
 */
function base64UrlDecode(segment: string): string | null {
	try {
		let base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
		const remainder = base64.length % 4;
		if (remainder > 0) {
			base64 += '='.repeat(4 - remainder);
		}
		const binary = atob(base64);
		const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
		return new TextDecoder().decode(bytes);
	} catch {
		return null;
	}
}

function asString(value: unknown): string | null {
	return typeof value === 'string' && value.length > 0 ? value : null;
}

function asStringArray(value: unknown): string[] {
	return Array.isArray(value)
		? value.filter((item): item is string => typeof item === 'string')
		: [];
}
