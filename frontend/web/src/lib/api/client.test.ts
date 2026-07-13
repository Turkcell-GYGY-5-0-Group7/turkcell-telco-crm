import { describe, expect, it, vi } from 'vitest';
import { API_V1, ApiError, BFF_V1, createApiClient, joinUrl } from './client';

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

/**
 * A typed fetch stub. Declaring the fetch signature keeps `mock.calls` typed as
 * `[input, init]` tuples so the URL/headers assertions below type-check.
 */
function stubFetch(respond: () => Response) {
	return vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(respond()));
}

describe('joinUrl', () => {
	it('joins an absolute base and a leading-slash path', () => {
		expect(joinUrl('http://localhost:8080', '/bff/v1/home')).toBe(
			'http://localhost:8080/bff/v1/home'
		);
	});

	it('trims a trailing slash on the base', () => {
		expect(joinUrl('http://localhost:8080/', '/bff/v1/home')).toBe(
			'http://localhost:8080/bff/v1/home'
		);
	});

	it('supports a same-origin base', () => {
		expect(joinUrl('/', '/bff/v1/invoices')).toBe('/bff/v1/invoices');
	});
});

describe('createApiClient URL construction', () => {
	it('targets /bff/v1/home for getHome', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ profile: {}, activeSubscriptions: [] }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getHome();

		expect(fetchImpl).toHaveBeenCalledTimes(1);
		const [url] = fetchImpl.mock.calls[0];
		expect(url).toBe('http://localhost:8080/bff/v1/home');
	});

	it('targets the /bff/v1 surface for every endpoint, never a domain host', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({}));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getAccount();
		await api.getInvoices();
		await api.getOnboardingCatalog();
		await api.placeOnboardingOrder({
			tariffId: 't-1',
			addonIds: [],
			customer: { fullName: 'A', email: 'a@example.com', phoneNumber: '+90' }
		});

		const urls = fetchImpl.mock.calls.map((call) => String(call[0]));
		expect(urls).toEqual([
			'http://localhost:8080/bff/v1/account',
			'http://localhost:8080/bff/v1/invoices',
			'http://localhost:8080/bff/v1/onboarding/catalog',
			'http://localhost:8080/bff/v1/onboarding/order'
		]);
		for (const url of urls) {
			expect(url).toContain(BFF_V1);
			expect(new URL(url).host).toBe('localhost:8080');
		}
	});

	it('honors a same-origin base URL', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ invoices: [] }));
		const api = createApiClient({ baseUrl: '/', fetch: fetchImpl });

		await api.getInvoices();

		expect(String(fetchImpl.mock.calls[0][0])).toBe('/bff/v1/invoices');
	});

	it('sends an Idempotency-Key on the onboarding order write', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ orderId: 'o-1', status: 'PENDING' }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder(
			{
				tariffId: 't-1',
				addonIds: ['a-1'],
				customer: { fullName: 'A', email: 'a@example.com', phoneNumber: '+90' }
			},
			'fixed-key-123'
		);

		const init = fetchImpl.mock.calls[0][1] as RequestInit;
		const headers = init.headers as Record<string, string>;
		expect(init.method).toBe('POST');
		expect(headers['Idempotency-Key']).toBe('fixed-key-123');
	});

	it('attaches a bearer token when the getAccessToken seam provides one', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({}));
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			getAccessToken: () => 'test-token'
		});

		await api.getHome();

		const init = fetchImpl.mock.calls[0][1] as RequestInit;
		const headers = init.headers as Record<string, string>;
		expect(headers.Authorization).toBe('Bearer test-token');
	});

	it('throws ApiError on a non-2xx response', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ message: 'nope' }, 500));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await expect(api.getHome()).rejects.toBeInstanceOf(ApiError);
	});
});

describe('gateway thin-slice methods (16.4.2)', () => {
	it('polls order status against the gateway /api/v1/orders/{id}, url-encoding the id', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ orderId: 'o 1', status: 'PENDING_PAYMENT' }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getOrderStatus('o 1');

		const [url, init] = fetchImpl.mock.calls[0];
		expect(String(url)).toBe('http://localhost:8080/api/v1/orders/o%201');
		expect(String(url)).toContain(API_V1);
		expect(new URL(String(url)).host).toBe('localhost:8080');
		expect((init as RequestInit).method).toBe('GET');
	});

	it('submits payment to the gateway /api/v1/payments with paymentRequestId as the Idempotency-Key', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ paymentId: 'p-1', status: 'COMPLETED' }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.submitPayment({
			orderId: 'o-1',
			customerId: 'c-1',
			amount: 149.9,
			paymentRequestId: 'pay-key-123'
		});

		const [url, init] = fetchImpl.mock.calls[0];
		const request = init as RequestInit;
		const headers = request.headers as Record<string, string>;
		expect(String(url)).toBe('http://localhost:8080/api/v1/payments');
		expect(request.method).toBe('POST');
		expect(headers['Idempotency-Key']).toBe('pay-key-123');
		expect(JSON.parse(String(request.body))).toEqual({
			orderId: 'o-1',
			customerId: 'c-1',
			amount: 149.9,
			paymentRequestId: 'pay-key-123'
		});
	});

	it('never targets a domain host for the thin-slice calls (gateway origin only)', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ orderId: 'o-1', status: 'CONFIRMED' }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getOrderStatus('o-1');
		await api.submitPayment({
			orderId: 'o-1',
			customerId: 'c-1',
			amount: 10,
			paymentRequestId: 'k'
		});

		for (const call of fetchImpl.mock.calls) {
			expect(new URL(String(call[0])).host).toBe('localhost:8080');
		}
	});
});
