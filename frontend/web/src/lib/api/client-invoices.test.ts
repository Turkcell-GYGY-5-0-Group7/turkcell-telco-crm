import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from './client';

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

function pdfResponse(bytes: string, status = 200): Response {
	return new Response(new Blob([bytes], { type: 'application/pdf' }), {
		status,
		headers: { 'content-type': 'application/pdf' }
	});
}

function stubFetch(respond: () => Response) {
	return vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(respond()));
}

describe('getInvoices paging (16.5.2)', () => {
	it('omits the query string when no page/size is given (BFF defaults apply)', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ invoices: [] }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getInvoices();

		expect(String(fetchImpl.mock.calls[0][0])).toBe('http://localhost:8080/bff/v1/invoices');
	});

	it('appends page and size when supplied', async () => {
		const fetchImpl = stubFetch(() => jsonResponse({ invoices: [] }));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.getInvoices(2, 10);

		expect(String(fetchImpl.mock.calls[0][0])).toBe(
			'http://localhost:8080/bff/v1/invoices?page=2&size=10'
		);
	});
});

describe('downloadInvoicePdf (16.5.2)', () => {
	it('GETs the composed gateway PDF url and returns the response body as a Blob', async () => {
		const fetchImpl = stubFetch(() => pdfResponse('%PDF-1.7 body'));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		const blob = await api.downloadInvoicePdf('/api/v1/invoices/inv-1/pdf');

		const [url, init] = fetchImpl.mock.calls[0];
		expect(String(url)).toBe('http://localhost:8080/api/v1/invoices/inv-1/pdf');
		expect((init as RequestInit).method).toBe('GET');
		expect(blob).toBeInstanceOf(Blob);
		expect(await blob.text()).toBe('%PDF-1.7 body');
	});

	it('sends an application/pdf Accept header and the bearer token the gateway route requires', async () => {
		const fetchImpl = stubFetch(() => pdfResponse('bytes'));
		const api = createApiClient({
			baseUrl: 'http://localhost:8080',
			fetch: fetchImpl,
			getAccessToken: () => 'test-token'
		});

		await api.downloadInvoicePdf('/api/v1/invoices/inv-1/pdf');

		const headers = (fetchImpl.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
		expect(headers.Accept).toBe('application/pdf');
		expect(headers.Authorization).toBe('Bearer test-token');
	});

	it('never targets a domain host (gateway origin only)', async () => {
		const fetchImpl = stubFetch(() => pdfResponse('bytes'));
		const api = createApiClient({ baseUrl: 'http://localhost:8080', fetch: fetchImpl });

		await api.downloadInvoicePdf('/api/v1/invoices/inv 1/pdf');

		expect(new URL(String(fetchImpl.mock.calls[0][0])).host).toBe('localhost:8080');
	});
});
