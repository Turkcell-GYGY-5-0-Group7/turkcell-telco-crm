import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client';

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

/** Fetch stub returning queued responses in order (one per call). */
function sequenceFetch(...responses: Response[]) {
	const queue = [...responses];
	return vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
		Promise.resolve(queue.shift() ?? jsonResponse({}, 200))
	);
}

describe('graceful 401 handling (16.3.3)', () => {
	it('silently renews once and retries the request on 401, then succeeds', async () => {
		const fetchImpl = sequenceFetch(
			jsonResponse({ message: 'expired' }, 401),
			jsonResponse({ profile: {}, activeSubscriptions: [], latestInvoice: null })
		);
		const renew = vi.fn(() => true);
		const onAuthRedirect = vi.fn();
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			renewAccessToken: renew,
			onAuthRedirect
		});

		await api.getHome();

		expect(renew).toHaveBeenCalledTimes(1);
		expect(fetchImpl).toHaveBeenCalledTimes(2); // original + one retry
		expect(onAuthRedirect).not.toHaveBeenCalled();
	});

	it('redirects to /login (never a raw 401) when renewal fails', async () => {
		const fetchImpl = sequenceFetch(jsonResponse({ message: 'expired' }, 401));
		const renew = vi.fn(() => false);
		const onAuthRedirect = vi.fn();
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			renewAccessToken: renew,
			onAuthRedirect
		});

		await expect(api.getAccount()).rejects.toBeInstanceOf(ApiError);
		expect(renew).toHaveBeenCalledTimes(1);
		expect(fetchImpl).toHaveBeenCalledTimes(1); // no retry when renewal fails
		expect(onAuthRedirect).toHaveBeenCalledTimes(1);
	});

	it('redirects when the retry after renewal still returns 401', async () => {
		const fetchImpl = sequenceFetch(jsonResponse({}, 401), jsonResponse({}, 401));
		const renew = vi.fn(() => true);
		const onAuthRedirect = vi.fn();
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			renewAccessToken: renew,
			onAuthRedirect
		});

		await expect(api.getInvoices()).rejects.toBeInstanceOf(ApiError);
		expect(renew).toHaveBeenCalledTimes(1);
		expect(fetchImpl).toHaveBeenCalledTimes(2); // original + one retry, then give up
		expect(onAuthRedirect).toHaveBeenCalledTimes(1);
	});

	it('does not intercept non-401 errors (no renewal, no redirect)', async () => {
		const fetchImpl = sequenceFetch(jsonResponse({ message: 'boom' }, 500));
		const renew = vi.fn(() => true);
		const onAuthRedirect = vi.fn();
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			renewAccessToken: renew,
			onAuthRedirect
		});

		await expect(api.getHome()).rejects.toBeInstanceOf(ApiError);
		expect(renew).not.toHaveBeenCalled();
		expect(onAuthRedirect).not.toHaveBeenCalled();
		expect(fetchImpl).toHaveBeenCalledTimes(1);
	});

	it('carries the freshly renewed bearer on the retried request', async () => {
		let token = 'stale';
		const fetchImpl = sequenceFetch(
			jsonResponse({}, 401),
			jsonResponse({ profile: {}, activeSubscriptions: [], latestInvoice: null })
		);
		const renew = vi.fn(() => {
			token = 'fresh';
			return true;
		});
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			getAccessToken: () => token,
			renewAccessToken: renew,
			onAuthRedirect: () => {}
		});

		await api.getHome();

		const firstHeaders = (fetchImpl.mock.calls[0][1] as RequestInit).headers as Record<
			string,
			string
		>;
		const retryHeaders = (fetchImpl.mock.calls[1][1] as RequestInit).headers as Record<
			string,
			string
		>;
		expect(firstHeaders.Authorization).toBe('Bearer stale');
		expect(retryHeaders.Authorization).toBe('Bearer fresh');
	});
});
