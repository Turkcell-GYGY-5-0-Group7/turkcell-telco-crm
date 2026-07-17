import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client';

/** A platform ApiResult<T> success envelope, as every /api/v1 service returns. */
function envelope(data: unknown, status = 200): Response {
	return new Response(JSON.stringify({ success: true, data }), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

function failureEnvelope(code: string, message: string, status = 200): Response {
	return new Response(JSON.stringify({ success: false, error: { code, message } }), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

function errorResponse(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

function stubFetch(respond: () => Response) {
	return vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(respond()));
}

function clientWith(fetchImpl: ReturnType<typeof stubFetch>) {
	return createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });
}

describe('gateway thin-slice URL construction', () => {
	it('builds the usage-history url with an ISO time window and limit', async () => {
		const fetchImpl = stubFetch(() =>
			envelope({ content: [], nextCursor: null, hasNext: false, limit: 50 })
		);
		await clientWith(fetchImpl).getUsageHistory(
			'sub-1',
			'2026-01-01T00:00:00.000Z',
			'2026-02-01T00:00:00.000Z',
			undefined,
			50
		);
		const url = new URL(String(fetchImpl.mock.calls[0][0]));
		expect(url.pathname).toBe('/api/v1/usage/subscriptions/sub-1/history');
		expect(url.searchParams.get('from')).toBe('2026-01-01T00:00:00.000Z');
		expect(url.searchParams.get('to')).toBe('2026-02-01T00:00:00.000Z');
		expect(url.searchParams.get('limit')).toBe('50');
	});

	it('lists subscriptions by customerId', async () => {
		const fetchImpl = stubFetch(() =>
			envelope({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
		);
		await clientWith(fetchImpl).listSubscriptions('cust-7');
		expect(String(fetchImpl.mock.calls[0][0])).toBe(
			'http://localhost:8080/api/v1/subscriptions?customerId=cust-7'
		);
	});

	it('targets the caller-scoped notification history path', async () => {
		const fetchImpl = stubFetch(() =>
			envelope({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
		);
		await clientWith(fetchImpl).getNotificationHistory('cust-7', 1, 20);
		const url = new URL(String(fetchImpl.mock.calls[0][0]));
		expect(url.pathname).toBe('/api/v1/notifications/users/cust-7/history');
		expect(url.searchParams.get('page')).toBe('1');
	});
});

describe('envelope unwrapping', () => {
	it('returns the inner data on a success envelope', async () => {
		const fetchImpl = stubFetch(() => envelope({ id: 'ord-1', status: 'PENDING' }));
		const order = await clientWith(fetchImpl).getOrder('ord-1');
		expect(order).toMatchObject({ id: 'ord-1', status: 'PENDING' });
	});

	it('throws ApiError(502) on a 2xx that is not a successful envelope', async () => {
		const fetchImpl = stubFetch(() => new Response('{}', { status: 200 }));
		await expect(clientWith(fetchImpl).getOrder('ord-1')).rejects.toMatchObject({
			name: 'ApiError',
			status: 502
		});
	});

	it('surfaces the server message when the gateway rejects a business rule', async () => {
		const fetchImpl = stubFetch(() =>
			failureEnvelope('INVALID_STATE', 'Order cannot be cancelled once fulfilled', 409)
		);
		try {
			await clientWith(fetchImpl).cancelOrder('ord-1', 'changed mind');
			throw new Error('expected a rejection');
		} catch (err) {
			expect(err).toBeInstanceOf(ApiError);
			expect((err as ApiError).status).toBe(409);
			expect((err as ApiError).serverMessage).toBe('Order cannot be cancelled once fulfilled');
		}
	});
});

describe('cancelOrder', () => {
	it('issues a DELETE with the reason query and unwraps the updated order', async () => {
		const fetchImpl = stubFetch(() => envelope({ id: 'ord-1', status: 'CANCELLED' }));
		const order = await clientWith(fetchImpl).cancelOrder('ord-1', 'changed mind');
		const [url, init] = fetchImpl.mock.calls[0];
		expect((init as RequestInit).method).toBe('DELETE');
		expect(new URL(String(url)).searchParams.get('reason')).toBe('changed mind');
		expect(order.status).toBe('CANCELLED');
	});
});

describe('updateNotificationPreference', () => {
	it('PUTs the opt-in flag to the channel path', async () => {
		const fetchImpl = stubFetch(() =>
			envelope({ id: 'p1', userId: 'cust-7', channel: 'SMS', optedIn: false, updatedAt: 'now' })
		);
		await clientWith(fetchImpl).updateNotificationPreference('cust-7', 'SMS', false);
		const [url, init] = fetchImpl.mock.calls[0];
		expect(String(url)).toBe(
			'http://localhost:8080/api/v1/notifications/users/cust-7/preferences/SMS'
		);
		expect((init as RequestInit).method).toBe('PUT');
		expect(JSON.parse(String((init as RequestInit).body))).toEqual({ optedIn: false });
	});
});

describe('assignTicket (ApiResult<Unit>)', () => {
	it('resolves for a success envelope with no data payload', async () => {
		const fetchImpl = stubFetch(() => envelope({}));
		await expect(
			clientWith(fetchImpl).assignTicket('t-1', 'tech-support')
		).resolves.toBeUndefined();
	});

	it('rejects when the gateway denies the assignment', async () => {
		const fetchImpl = stubFetch(() => errorResponse({ success: false }, 403));
		await expect(clientWith(fetchImpl).assignTicket('t-1', 'tech-support')).rejects.toMatchObject({
			status: 403
		});
	});
});
