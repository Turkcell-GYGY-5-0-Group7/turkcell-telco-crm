import { describe, expect, it } from 'vitest';
import { claimsOf, decodeJwtPayload, hasAnyRole, isAdmin, isStaff } from './jwt-claims';

/** Build an unsigned JWT (`header.payload.signature`) with the given payload. */
function tokenWith(payload: Record<string, unknown>): string {
	const encode = (obj: unknown) => {
		const bytes = new TextEncoder().encode(JSON.stringify(obj));
		let binary = '';
		for (const byte of bytes) binary += String.fromCharCode(byte);
		return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
	};
	return `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

describe('decodeJwtPayload', () => {
	it('decodes a well-formed token payload', () => {
		const token = tokenWith({ sub: 'user-1', roles: ['SUBSCRIBER'] });
		expect(decodeJwtPayload(token)).toEqual({ sub: 'user-1', roles: ['SUBSCRIBER'] });
	});

	it('returns null for null/empty/malformed tokens', () => {
		expect(decodeJwtPayload(null)).toBeNull();
		expect(decodeJwtPayload('')).toBeNull();
		expect(decodeJwtPayload('not-a-jwt')).toBeNull();
		expect(decodeJwtPayload('only.two')).toBeNull();
	});

	it('returns null when the payload is not valid JSON', () => {
		expect(decodeJwtPayload('aaa.!!!not-base64json!!!.bbb')).toBeNull();
	});

	it('decodes UTF-8 names in the payload', () => {
		const token = tokenWith({ preferred_username: 'Barış Polat' });
		expect(decodeJwtPayload(token)).toEqual({ preferred_username: 'Barış Polat' });
	});
});

describe('claimsOf', () => {
	it('extracts sub, customerId, roles and preferred_username', () => {
		const token = tokenWith({
			sub: 'user-1',
			customerId: 'cust-9',
			roles: ['SUBSCRIBER', 'ADMIN'],
			preferred_username: 'demo'
		});
		expect(claimsOf(token)).toEqual({
			sub: 'user-1',
			customerId: 'cust-9',
			roles: ['SUBSCRIBER', 'ADMIN'],
			preferredUsername: 'demo'
		});
	});

	it('degrades missing claims to safe defaults', () => {
		expect(claimsOf(tokenWith({}))).toEqual({
			sub: null,
			customerId: null,
			roles: [],
			preferredUsername: null
		});
	});

	it('treats a null customerId claim (unlinked) as null', () => {
		expect(claimsOf(tokenWith({ customerId: null, roles: ['SUBSCRIBER'] })).customerId).toBeNull();
	});

	it('drops non-string entries from the roles claim', () => {
		expect(claimsOf(tokenWith({ roles: ['ADMIN', 42, null] })).roles).toEqual(['ADMIN']);
	});

	it('returns empty claims for an absent token', () => {
		expect(claimsOf(null).roles).toEqual([]);
	});
});

describe('role predicates', () => {
	it('hasAnyRole matches on any listed role', () => {
		const claims = claimsOf(tokenWith({ roles: ['SUBSCRIBER'] }));
		expect(hasAnyRole(claims, ['ADMIN', 'SUBSCRIBER'])).toBe(true);
		expect(hasAnyRole(claims, ['ADMIN'])).toBe(false);
	});

	it('isStaff is true for any non-subscriber staff role and false for a plain subscriber', () => {
		expect(isStaff(claimsOf(tokenWith({ roles: ['CALL_CENTER_AGENT'] })))).toBe(true);
		expect(isStaff(claimsOf(tokenWith({ roles: ['ADMIN'] })))).toBe(true);
		expect(isStaff(claimsOf(tokenWith({ roles: ['SUBSCRIBER'] })))).toBe(false);
	});

	it('isAdmin is true only with the ADMIN role', () => {
		expect(isAdmin(claimsOf(tokenWith({ roles: ['ADMIN'] })))).toBe(true);
		expect(isAdmin(claimsOf(tokenWith({ roles: ['BILLING_OPERATOR'] })))).toBe(false);
	});
});
