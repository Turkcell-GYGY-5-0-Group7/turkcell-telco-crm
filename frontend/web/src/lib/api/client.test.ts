import { describe, expect, it, vi } from 'vitest';
import {
	API_V1,
	ApiError,
	BFF_V1,
	createApiClient,
	joinUrl,
	type OnboardingOrderRequest
} from './client';

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

/**
 * A REGISTER-path onboarding order exactly as the BFF's `OnboardingOrderRequest`
 * record declares it (customerId | customer + kycDocument, tariffCode, addonCodes).
 * Kept as the single fixture so a drift from the Java contract fails these tests.
 */
const registerOrder: OnboardingOrderRequest = {
	customer: {
		type: 'INDIVIDUAL',
		firstName: 'Ada',
		lastName: 'Lovelace',
		identityNumber: '10000000146',
		dateOfBirth: '1990-01-01'
	},
	kycDocument: {
		type: 'ID_CARD',
		fileName: 'id.png',
		contentType: 'image/png',
		content: 'QUJD'
	},
	tariffCode: 'POSTPAID-M',
	addonCodes: []
};

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
		await api.placeOnboardingOrder(registerOrder);

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
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-1', status: 'PENDING', idempotencyKey: 'fixed-key-123' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder(registerOrder, 'fixed-key-123');

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

// These tests pin the wire shape to the BFF's ACTUAL Java DTOs
// (microservices/web-bff/src/main/java/com/telco/webbff/dto/). The wizard once shipped
// with a guessed shape (tariffId / addonIds / fullName+email+phone) that the BFF would
// have rejected, so the request body is asserted field by field here.
describe('POST /bff/v1/onboarding/order body (BFF OnboardingOrderRequest)', () => {
	function bodyOf(fetchImpl: ReturnType<typeof stubFetch>): Record<string, unknown> {
		const init = fetchImpl.mock.calls[0][1] as RequestInit;
		return JSON.parse(String(init.body));
	}

	it('sends the REGISTER path exactly as the BFF record declares it', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-1', status: 'PENDING', idempotencyKey: 'k-1' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder(registerOrder, 'k-1');

		expect(bodyOf(fetchImpl)).toEqual({
			customer: {
				type: 'INDIVIDUAL',
				firstName: 'Ada',
				lastName: 'Lovelace',
				identityNumber: '10000000146',
				dateOfBirth: '1990-01-01'
			},
			kycDocument: {
				type: 'ID_CARD',
				fileName: 'id.png',
				contentType: 'image/png',
				content: 'QUJD'
			},
			tariffCode: 'POSTPAID-M',
			addonCodes: []
		});
	});

	it('carries tariffCode / addonCodes - never tariffId / addonIds', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-1', status: 'PENDING', idempotencyKey: 'k-1' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder({ ...registerOrder, addonCodes: ['DATA-5GB'] });

		const body = bodyOf(fetchImpl);
		expect(body.tariffCode).toBe('POSTPAID-M');
		expect(body.addonCodes).toEqual(['DATA-5GB']);
		expect(body).not.toHaveProperty('tariffId');
		expect(body).not.toHaveProperty('addonIds');
	});

	it('never sends fullName / email / phoneNumber - the register contract has no such fields', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-1', status: 'PENDING', idempotencyKey: 'k-1' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder(registerOrder);

		const customer = bodyOf(fetchImpl).customer as Record<string, unknown>;
		expect(Object.keys(customer).sort()).toEqual([
			'dateOfBirth',
			'firstName',
			'identityNumber',
			'lastName',
			'type'
		]);
	});

	it('sends the KYC document with the BFF field names (type/fileName/contentType/content)', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-1', status: 'PENDING', idempotencyKey: 'k-1' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder(registerOrder);

		const document = bodyOf(fetchImpl).kycDocument as Record<string, unknown>;
		expect(Object.keys(document).sort()).toEqual(['content', 'contentType', 'fileName', 'type']);
		expect(document.content).toBe('QUJD');
		expect(document).not.toHaveProperty('filename');
		expect(document).not.toHaveProperty('base64');
	});

	it('sends the REUSE path (customerId only, no register/KYC block)', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-2', status: 'PENDING', idempotencyKey: 'k-2' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.placeOnboardingOrder({
			customerId: 'c-1',
			tariffCode: 'POSTPAID-M',
			addonCodes: []
		});

		expect(bodyOf(fetchImpl)).toEqual({
			customerId: 'c-1',
			tariffCode: 'POSTPAID-M',
			addonCodes: []
		});
	});

	it('reads back the BFF OnboardingOrderResponse (orderId/status/idempotencyKey, no customerId)', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({ orderId: 'o-9', status: 'PENDING', idempotencyKey: 'k-9' })
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		const result = await api.placeOnboardingOrder(registerOrder, 'k-9');

		expect(result).toEqual({ orderId: 'o-9', status: 'PENDING', idempotencyKey: 'k-9' });
	});
});

// GET /bff/v1/onboarding/catalog, as verified live: tariffs carry `code` (not
// tariffId) and addons carry `code`/`price`/`tariffCode` (BFF TariffOption/AddonOption).
describe('GET /bff/v1/onboarding/catalog (BFF OnboardingCatalogResponse)', () => {
	it('parses the catalog the BFF actually returns', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse({
				tariffs: [
					{
						code: 'POSTPAID-M',
						name: 'Postpaid Medium',
						description: 'INDIVIDUAL',
						monthlyPrice: 199.9,
						currency: 'TRY'
					}
				],
				addons: [
					{
						code: 'DATA-5GB',
						name: 'Extra 5GB',
						tariffCode: 'POSTPAID-M',
						price: 49.9,
						currency: 'TRY'
					}
				]
			})
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		const catalog = await api.getOnboardingCatalog();

		expect(catalog.tariffs[0].code).toBe('POSTPAID-M');
		expect(catalog.tariffs[0].monthlyPrice).toBe(199.9);
		expect(catalog.addons[0].code).toBe('DATA-5GB');
		expect(catalog.addons[0].tariffCode).toBe('POSTPAID-M');
		expect(catalog.addons[0].price).toBe(49.9);
	});
});

describe('gateway thin-slice: order-status polling', () => {
	// order-service answers with the ADR-015 envelope and names the id `id`; the client
	// is the single place that unwraps/normalizes it.
	function orderEnvelope(id: string, status: string, customerId = 'c-1') {
		return {
			success: true,
			data: {
				id,
				customerId,
				status,
				idempotencyKey: 'k-1',
				totalAmount: 199.9,
				items: []
			},
			meta: { traceId: 't-1' }
		};
	}

	it('polls the gateway /api/v1/orders/{id}, url-encoding the id', async () => {
		const fetchImpl = stubFetch(() => jsonResponse(orderEnvelope('o 1', 'PENDING')));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getOrderStatus('o 1');

		const [url, init] = fetchImpl.mock.calls[0];
		expect(String(url)).toBe('http://localhost:8080/api/v1/orders/o%201');
		expect(String(url)).toContain(API_V1);
		expect(new URL(String(url)).host).toBe('localhost:8080');
		expect((init as RequestInit).method).toBe('GET');
	});

	it('unwraps ApiResult<OrderResponse> and maps `id` -> orderId, keeping customerId', async () => {
		const fetchImpl = stubFetch(() => jsonResponse(orderEnvelope('o-1', 'FULFILLED', 'c-42')));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		const status = await api.getOrderStatus('o-1');

		expect(status).toEqual({ orderId: 'o-1', customerId: 'c-42', status: 'FULFILLED' });
	});

	it('fails loudly on a 2xx that is not a successful envelope, rather than faking a status', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ success: false, error: { code: 'X' } }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await expect(api.getOrderStatus('o-1')).rejects.toBeInstanceOf(ApiError);
	});

	it('exposes no payment call at all (charging is event-driven; POST /api/v1/payments is ADMIN-only)', () => {
		const api = createApiClient({ baseUrl: 'http://localhost:8080' });

		expect('submitPayment' in api).toBe(false);
	});
});

describe('ApiError.serverMessage', () => {
	it('surfaces the platform ApiResult error message (e.g. a rejected TCKN)', async () => {
		const fetchImpl = stubFetch(() =>
			jsonResponse(
				{
					success: false,
					error: {
						code: 'VALIDATION_ERROR',
						message: 'gateway call to /api/v1/customers failed with status 400'
					}
				},
				400
			)
		);
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		const error = await api.placeOnboardingOrder(registerOrder).catch((e: unknown) => e);

		expect(error).toBeInstanceOf(ApiError);
		expect((error as ApiError).serverMessage).toBe(
			'gateway call to /api/v1/customers failed with status 400'
		);
	});

	it('is null when the body carries no error message', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ message: 'nope' }, 500));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		const error = await api.getHome().catch((e: unknown) => e);

		expect((error as ApiError).serverMessage).toBeNull();
	});
});
