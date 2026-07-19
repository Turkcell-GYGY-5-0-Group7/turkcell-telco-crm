import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, setAccessTokenProvider } from '$lib/api/client';
import { clearAccessToken, getAccessToken, setAccessToken } from './token-store';

// Verifies the 16.3.1 wiring end to end at the framework-agnostic layer: the
// OIDC token cache (token-store) feeds the SINGLE BFF client's getAccessToken
// seam, so authenticated calls carry a bearer - without a live Keycloak or DOM.

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

function stubFetch() {
	return vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
		Promise.resolve(jsonResponse({ profile: {}, activeSubscriptions: [] }))
	);
}

afterEach(() => {
	setAccessTokenProvider(() => null);
	clearAccessToken();
	vi.unstubAllGlobals();
});

describe('BFF bearer-token seam wiring (16.3.1)', () => {
	it('sends no Authorization header when no token is cached', async () => {
		const fetchImpl = stubFetch();
		vi.stubGlobal('fetch', fetchImpl);

		await api.getHome();

		const headers = (fetchImpl.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
		expect(headers.Authorization).toBeUndefined();
	});

	it('attaches the cached token as a bearer once the provider is registered', async () => {
		const fetchImpl = stubFetch();
		vi.stubGlobal('fetch', fetchImpl);

		// Mirror oidc.ts#initAuth: register the token-store reader as the provider.
		setAccessTokenProvider(getAccessToken);
		setAccessToken('kc-access-token');

		await api.getHome();

		const headers = (fetchImpl.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
		expect(headers.Authorization).toBe('Bearer kc-access-token');
	});

	it('drops the bearer after the token is cleared (logout / expiry)', async () => {
		const fetchImpl = stubFetch();
		vi.stubGlobal('fetch', fetchImpl);

		setAccessTokenProvider(getAccessToken);
		setAccessToken('kc-access-token');
		clearAccessToken();

		await api.getHome();

		const headers = (fetchImpl.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
		expect(headers.Authorization).toBeUndefined();
	});
});
