import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '$lib/api/client';
import {
	isCustomerUnlinkedError,
	loadLinkedResource,
	shouldRefreshSessionAfterOnboarding
} from './link-state';

// The first-run defect these tests pin down: a signed-in user who has not
// onboarded holds a token with roles [SUBSCRIBER] but customerId: null, so the
// web-bff self-scoping guard correctly answers the account reads with 403. The UI
// used to render that as "Could not load your dashboard. (HTTP 403)". It is an
// expected application state, and it must be told apart from a REAL failure -
// which these tests assert is still surfaced as an error.

/** The exact envelope web-bff returns for an unlinked identity (ADR-015). */
const UNLINKED_BODY = {
	success: false,
	error: {
		code: 'ACCESS_DENIED',
		message: 'authenticated identity is not linked to a customer record'
	}
};

const unlinkedError = () => new ApiError(403, 'http://localhost:8080/bff/v1/home', UNLINKED_BODY);

const serverError = () =>
	new ApiError(500, 'http://localhost:8080/bff/v1/home', {
		success: false,
		error: { code: 'INTERNAL_ERROR', message: 'boom' }
	});

/** What fetch throws when the network is down (never an ApiError). */
const networkError = () => new TypeError('Failed to fetch');

interface Home {
	profile: { customerId: string };
}

const HOME: Home = { profile: { customerId: 'cus-1' } };

describe('isCustomerUnlinkedError (403 on a self-scoped BFF account read)', () => {
	it('recognises the BFF 403 as "not linked to a customer yet"', () => {
		expect(isCustomerUnlinkedError(unlinkedError())).toBe(true);
	});

	it('carries the server message through ApiError for diagnostics', () => {
		expect(unlinkedError().serverMessage).toBe(
			'authenticated identity is not linked to a customer record'
		);
	});

	it('does NOT swallow a server error (500)', () => {
		expect(isCustomerUnlinkedError(serverError())).toBe(false);
	});

	it('does NOT swallow a network failure', () => {
		expect(isCustomerUnlinkedError(networkError())).toBe(false);
	});

	it('does NOT treat a 401 as unlinked (that is the auth layer silent-renew path)', () => {
		expect(isCustomerUnlinkedError(new ApiError(401, '/bff/v1/home', null))).toBe(false);
	});

	it('does NOT treat a 404 or 502 as unlinked', () => {
		expect(isCustomerUnlinkedError(new ApiError(404, '/bff/v1/home', null))).toBe(false);
		expect(isCustomerUnlinkedError(new ApiError(502, '/bff/v1/home', null))).toBe(false);
	});

	it('ignores non-error values', () => {
		expect(isCustomerUnlinkedError(null)).toBe(false);
		expect(isCustomerUnlinkedError(undefined)).toBe(false);
		expect(isCustomerUnlinkedError('403')).toBe(false);
		expect(isCustomerUnlinkedError({ status: 403 })).toBe(false);
	});
});

describe('loadLinkedResource', () => {
	it('returns the payload when the read succeeds (200 -> normal render)', async () => {
		const read = vi.fn(() => Promise.resolve(HOME));

		const result = await loadLinkedResource(read);

		expect(result).toEqual({ state: 'loaded', data: HOME });
		expect(read).toHaveBeenCalledTimes(1);
	});

	it('reports the unlinked state on a 403 (no renew seam supplied)', async () => {
		const read = vi.fn(() => Promise.reject(unlinkedError()));

		const result = await loadLinkedResource(read);

		expect(result).toEqual({ state: 'unlinked' });
		expect(read).toHaveBeenCalledTimes(1);
	});

	it('surfaces a 500 as a real error, never as the onboarding state', async () => {
		const error = serverError();
		const read = vi.fn(() => Promise.reject(error));
		const renewSession = vi.fn(() => Promise.resolve(true));

		const result = await loadLinkedResource(read, { renewSession });

		expect(result).toEqual({ state: 'error', error });
		// A genuine failure must not spend a token renewal or a retry.
		expect(renewSession).not.toHaveBeenCalled();
		expect(read).toHaveBeenCalledTimes(1);
	});

	it('surfaces a network failure as a real error', async () => {
		const error = networkError();
		const read = vi.fn(() => Promise.reject(error));

		const result = await loadLinkedResource(read, { renewSession: () => true });

		expect(result).toEqual({ state: 'error', error });
	});

	// The stale-token race: onboarding just completed, but the token in hand was
	// minted before identity-service consumed customer.registered.v1.
	it('renews once and retries when unlinked; the refreshed token loads the account', async () => {
		const read = vi
			.fn<() => Promise<Home>>()
			.mockRejectedValueOnce(unlinkedError())
			.mockResolvedValueOnce(HOME);
		const renewSession = vi.fn(() => Promise.resolve(true));

		const result = await loadLinkedResource(read, { renewSession });

		expect(result).toEqual({ state: 'loaded', data: HOME });
		expect(renewSession).toHaveBeenCalledTimes(1);
		expect(read).toHaveBeenCalledTimes(2);
	});

	it('falls back to the onboarding state when the renewed token is STILL unlinked (no loop)', async () => {
		const read = vi.fn(() => Promise.reject(unlinkedError()));
		const renewSession = vi.fn(() => Promise.resolve(true));

		const result = await loadLinkedResource(read, { renewSession });

		expect(result).toEqual({ state: 'unlinked' });
		// Exactly one renew and exactly one retry - never a loop.
		expect(renewSession).toHaveBeenCalledTimes(1);
		expect(read).toHaveBeenCalledTimes(2);
	});

	it('falls back to the onboarding state when the renew fails (no retry)', async () => {
		const read = vi.fn(() => Promise.reject(unlinkedError()));
		const renewSession = vi.fn(() => Promise.resolve(false));

		const result = await loadLinkedResource(read, { renewSession });

		expect(result).toEqual({ state: 'unlinked' });
		expect(read).toHaveBeenCalledTimes(1);
	});

	it('treats a renew that throws as a failed renew, not as a load error', async () => {
		const read = vi.fn(() => Promise.reject(unlinkedError()));
		const renewSession = vi.fn(() => Promise.reject(new Error('refresh token revoked')));

		const result = await loadLinkedResource(read, { renewSession });

		expect(result).toEqual({ state: 'unlinked' });
	});

	it('still reports a real error raised by the RETRY after a renew', async () => {
		const error = serverError();
		const read = vi
			.fn<() => Promise<Home>>()
			.mockRejectedValueOnce(unlinkedError())
			.mockRejectedValueOnce(error);

		const result = await loadLinkedResource(read, { renewSession: () => true });

		expect(result).toEqual({ state: 'error', error });
	});

	it('accepts a synchronous renew seam', async () => {
		const read = vi
			.fn<() => Promise<Home>>()
			.mockRejectedValueOnce(unlinkedError())
			.mockResolvedValueOnce(HOME);

		const result = await loadLinkedResource(read, { renewSession: () => true });

		expect(result).toEqual({ state: 'loaded', data: HOME });
	});
});

describe('shouldRefreshSessionAfterOnboarding', () => {
	it('refreshes the session when the saga activated the subscription', () => {
		expect(shouldRefreshSessionAfterOnboarding({ outcome: 'activated' })).toBe(true);
	});

	it('does not spend a refresh grant on a failed or pending outcome', () => {
		expect(shouldRefreshSessionAfterOnboarding({ outcome: 'failed' })).toBe(false);
		expect(shouldRefreshSessionAfterOnboarding({ outcome: 'pending' })).toBe(false);
	});

	it('does not refresh when there is no poll result', () => {
		expect(shouldRefreshSessionAfterOnboarding(null)).toBe(false);
		expect(shouldRefreshSessionAfterOnboarding(undefined)).toBe(false);
	});
});
