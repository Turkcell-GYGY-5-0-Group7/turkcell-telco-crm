// Typed Backend-for-Frontend (BFF) API client for the Telco CRM web app.
//
// ADR-022 / ADR-011 Section 2: the browser never calls a domain service
// directly. This module is the SINGLE place in the frontend that constructs
// outbound request URLs. Composed reads target the BFF surface (`/bff/v1/**`).
// A few writes/reads that the web-bff does not yet compose fall back to the API
// GATEWAY surface (`/api/v1/**`) under ADR-022's thin-slice allowance - namely
// order-status polling (`GET /api/v1/orders/{id}`) and payment submission
// (`POST /api/v1/payments`), which the onboarding wizard invokes directly. Both
// surfaces resolve against the same configurable gateway base URL; a
// domain-service host/port is never targeted, and this remains the only HTTP
// path in the frontend.
//
// Auth wiring (bearer token) is subtask 16.3. This client leaves a clear seam:
// an injectable `getAccessToken` hook. Until 16.3 it defaults to returning
// null, so no Authorization header is attached.

import { env } from '$env/dynamic/public';

/**
 * Default outbound origin for local development. Per ADR-022's thin-slice
 * allowance the browser MAY call the API gateway directly until the web-bff
 * composes an endpoint; the gateway listens on 8080 locally. Override in
 * production with `PUBLIC_BFF_BASE_URL` (e.g. a same-origin '/').
 */
export const DEFAULT_BASE_URL = 'http://localhost:8080';

/** BFF API version prefix (ADR-015 / web-bff contract). */
export const BFF_V1 = '/bff/v1';

/**
 * API gateway version prefix. Used only for the two thin-slice calls the web-bff
 * does not compose (order-status polling, payment submission); see the module
 * header and ADR-022.
 */
export const API_V1 = '/api/v1';

/**
 * Resolve the configured outbound base URL. Reads the env-driven `PUBLIC_`
 * value at call time, falling back to the dev default.
 */
export function resolveBaseUrl(): string {
	const configured = env.PUBLIC_BFF_BASE_URL?.trim();
	return configured && configured.length > 0 ? configured : DEFAULT_BASE_URL;
}

/**
 * Join a base URL and an absolute API path into a single request URL. A base
 * of '/' (or '') yields a same-origin path such as `/bff/v1/home`.
 */
export function joinUrl(baseUrl: string, path: string): string {
	const trimmedBase = baseUrl.replace(/\/+$/, '');
	const trimmedPath = path.startsWith('/') ? path : `/${path}`;
	return `${trimmedBase}${trimmedPath}`;
}

/** Injectable seam for 16.3 auth. Returns the bearer token, or null if none. */
export type GetAccessToken = () => string | null | Promise<string | null>;

/**
 * Process-wide bearer-token provider for the default {@link api} instance.
 * Defaults to returning null (SSR-safe, no auth). The browser auth layer
 * (`$lib/auth/oidc.ts`, subtask 16.3.1) registers the real provider via
 * {@link setAccessTokenProvider} once the OIDC session is initialised, so
 * authenticated BFF calls carry `Authorization: Bearer <access_token>` through
 * this single client - no second HTTP path is introduced.
 */
let accessTokenProvider: GetAccessToken = () => null;

/** Register the process-wide bearer-token provider used by {@link api}. */
export function setAccessTokenProvider(provider: GetAccessToken): void {
	accessTokenProvider = provider;
}

/**
 * Seam for graceful 401 handling (16.3.3): attempt a silent token renewal.
 * Resolves `true` when a fresh, usable access token is now available (so the
 * failed request can be retried once); `false` when renewal failed.
 */
export type RenewAccessToken = () => boolean | Promise<boolean>;

/**
 * Seam for graceful 401 handling (16.3.3): perform a clean redirect to `/login`,
 * preserving the current path as the post-login return target (16.3.2). Invoked
 * only when silent renewal cannot recover the session, so the user is re-authed
 * instead of ever seeing a raw 401.
 */
export type AuthRedirect = () => void;

/**
 * Process-wide silent-renew handler for the default {@link api} instance. The
 * browser auth layer (`$lib/auth/oidc.ts`) registers the real oidc-client-ts
 * `signinSilent` path via {@link setRenewAccessTokenHandler} in `initAuth`.
 * Defaults to `false` (SSR-safe: no renewal off the browser).
 */
let renewAccessTokenProvider: RenewAccessToken = () => false;

/**
 * Process-wide auth-redirect handler for the default {@link api} instance. The
 * browser auth layer registers a `/login` redirect (return-to preserved) via
 * {@link setAuthRedirectHandler}. Defaults to a no-op (SSR-safe).
 */
let authRedirectHandler: AuthRedirect = () => {};

/** Register the process-wide silent-renew handler used by {@link api}. */
export function setRenewAccessTokenHandler(handler: RenewAccessToken): void {
	renewAccessTokenProvider = handler;
}

/** Register the process-wide auth-redirect handler used by {@link api}. */
export function setAuthRedirectHandler(handler: AuthRedirect): void {
	authRedirectHandler = handler;
}

export interface ApiClientOptions {
	/** Outbound base URL. Defaults to {@link resolveBaseUrl}. */
	baseUrl?: string;
	/** Fetch implementation (injectable for SSR `load` and tests). */
	fetch?: typeof globalThis.fetch;
	/** Bearer-token provider seam (16.3). Defaults to returning null. */
	getAccessToken?: GetAccessToken;
	/** Silent-renew seam (16.3.3). Defaults to returning false (no renewal). */
	renewAccessToken?: RenewAccessToken;
	/** Auth-redirect seam (16.3.3). Defaults to a no-op. */
	onAuthRedirect?: AuthRedirect;
}

/** Error thrown when the BFF/gateway returns a non-2xx response. */
export class ApiError extends Error {
	readonly status: number;
	readonly url: string;
	readonly body: unknown;

	constructor(status: number, url: string, body: unknown) {
		super(`BFF request failed: ${status} ${url}`);
		this.name = 'ApiError';
		this.status = status;
		this.url = url;
		this.body = body;
	}
}

// ---------------------------------------------------------------------------
// UI-shaped response types (derived from docs/api-contracts/web-bff.md).
// These are the shapes the BFF composes for the web channel; they intentionally
// do not use ApiResult<T> (the BFF responds UI-shaped, per the contract notes).
// ---------------------------------------------------------------------------

export interface Profile {
	customerId: string;
	fullName: string;
	status: string;
}

export interface Subscription {
	subscriptionId: string;
	msisdn: string;
	tariffCode: string;
	status: string;
}

export interface InvoiceSummary {
	invoiceId: string;
	period: string;
	amount: number;
	currency: string;
	status: string;
	/**
	 * Usable PDF-download link composed by the BFF: the gateway route
	 * `/api/v1/invoices/{id}/pdf`. It requires the caller's bearer token, so it is
	 * fetched through {@link ApiClient.downloadInvoicePdf} (single HTTP path), never
	 * a plain `<a href>` that would omit the Authorization header.
	 */
	pdfUrl: string;
}

/**
 * Current billing-period usage against the plan allowance for one subscription,
 * composed from usage-service. Mirrors the BFF `UsageSummary` record; every value
 * is a whole unit (MB, minutes, SMS count).
 */
export interface UsageSummary {
	dataUsedMb: number;
	dataAllowanceMb: number;
	voiceUsedMinutes: number;
	voiceAllowanceMinutes: number;
	smsUsed: number;
	smsAllowance: number;
}

/**
 * One account row: a subscription paired with its usage/quota. `usage` is present
 * only for ACTIVE subscriptions (usage-service provisions a quota on activation)
 * and is `null` for suspended/terminated lines, which carry no live quota.
 */
export interface AccountSubscription {
	subscription: Subscription;
	usage: UsageSummary | null;
}

/** GET /bff/v1/home */
export interface HomeDashboard {
	profile: Profile;
	activeSubscriptions: Subscription[];
	latestInvoice: InvoiceSummary | null;
}

/** GET /bff/v1/account (BFF `AccountResponse`). */
export interface AccountOverview {
	profile: Profile;
	subscriptions: AccountSubscription[];
}

/** GET /bff/v1/invoices (BFF `InvoicesResponse`) - a page of invoices plus paging metadata. */
export interface InvoiceList {
	invoices: InvoiceSummary[];
	/** Zero-based page index echoed by the BFF. */
	page: number;
	/** Page size echoed by the BFF. */
	size: number;
	/** Total invoices across all pages. */
	totalElements: number;
	/** Total page count for the current page size. */
	totalPages: number;
}

export interface Tariff {
	tariffId: string;
	name: string;
	monthlyPrice: number;
	currency: string;
}

export interface Addon {
	addonId: string;
	name: string;
	monthlyPrice: number;
	currency: string;
}

/** GET /bff/v1/onboarding/catalog */
export interface OnboardingCatalog {
	tariffs: Tariff[];
	addons: Addon[];
}

/**
 * A KYC identity document captured in the wizard and forwarded to the BFF, which
 * relays it to customer-service (`POST /api/v1/customers/{id}/documents`). The
 * bytes are base64-encoded so the document rides the single JSON order call; the
 * BFF owns the multipart upload downstream.
 */
export interface KycDocumentPayload {
	filename: string;
	contentType: string;
	/** Base64-encoded document bytes. */
	content: string;
}

/** POST /bff/v1/onboarding/order request body. */
export interface OnboardingOrderRequest {
	tariffId: string;
	addonIds: string[];
	customer: {
		fullName: string;
		email: string;
		phoneNumber: string;
		/** TCKN (individual) or VKN (corporate); optional until KYC is enforced. */
		nationalId?: string;
	};
	/** KYC document uploaded in the wizard's KYC step; omitted when not captured. */
	kycDocument?: KycDocumentPayload;
}

/** POST /bff/v1/onboarding/order response. */
export interface OnboardingOrderResult {
	orderId: string;
	status: string;
	/**
	 * The registered (or reused) customer this order belongs to. Carried so the
	 * wizard's payment step can charge without a second identity lookup.
	 */
	customerId: string;
}

/** GET /api/v1/orders/{id} - order + saga status, polled by the wizard. */
export interface OrderStatus {
	orderId: string;
	status: string;
}

/**
 * POST /api/v1/payments request body (payment-service contract). `paymentRequestId`
 * IS the mandatory idempotency key and is echoed into the `Idempotency-Key` header.
 */
export interface PaymentRequest {
	orderId: string;
	customerId: string;
	amount: number;
	paymentRequestId: string;
}

/** POST /api/v1/payments response. */
export interface PaymentResult {
	paymentId: string;
	status: string;
}

interface RequestOptions {
	method: string;
	path: string;
	body?: unknown;
	/** Extra headers, e.g. the write-side Idempotency-Key. */
	headers?: Record<string, string>;
}

/**
 * The typed BFF client. Construct via {@link createApiClient} or use the
 * default {@link api} instance. Every public method routes through the private
 * {@link ApiClient.request} method, which is the ONLY code path that builds an
 * outbound URL.
 */
export class ApiClient {
	private readonly baseUrl: string;
	private readonly fetchImpl: typeof globalThis.fetch;
	private readonly getAccessToken: GetAccessToken;
	private readonly renewAccessToken: RenewAccessToken;
	private readonly onAuthRedirect: AuthRedirect;

	constructor(options: ApiClientOptions = {}) {
		this.baseUrl = options.baseUrl ?? resolveBaseUrl();
		// When no fetch is injected, resolve the global at CALL time (late binding)
		// rather than capturing it at construction. This keeps the default `api`
		// singleton correct under fetch polyfills / test stubs, while an explicitly
		// injected fetch (SSR `load`) is still used verbatim.
		this.fetchImpl = options.fetch ?? ((input, init) => globalThis.fetch(input, init));
		this.getAccessToken = options.getAccessToken ?? (() => null);
		this.renewAccessToken = options.renewAccessToken ?? (() => false);
		this.onAuthRedirect = options.onAuthRedirect ?? (() => {});
	}

	// -- BFF endpoints (docs/api-contracts/web-bff.md) ----------------------

	/** Dashboard: profile + active subscriptions + latest invoice. */
	getHome(): Promise<HomeDashboard> {
		return this.request<HomeDashboard>({ method: 'GET', path: `${BFF_V1}/home` });
	}

	/** Profile, subscriptions, and usage summary in one response. */
	getAccount(): Promise<AccountOverview> {
		return this.request<AccountOverview>({ method: 'GET', path: `${BFF_V1}/account` });
	}

	/**
	 * A page of the caller's invoices, each carrying a usable PDF-download link.
	 * `page`/`size` are optional: when omitted the BFF applies its own defaults
	 * (page 0, size 20) and the request carries no query string; when supplied
	 * they select a specific page for the UI's pager.
	 */
	getInvoices(page?: number, size?: number): Promise<InvoiceList> {
		const params = new URLSearchParams();
		if (page !== undefined) {
			params.set('page', String(page));
		}
		if (size !== undefined) {
			params.set('size', String(size));
		}
		const query = params.toString();
		const path = query ? `${BFF_V1}/invoices?${query}` : `${BFF_V1}/invoices`;
		return this.request<InvoiceList>({ method: 'GET', path });
	}

	/**
	 * Download one invoice's PDF as a Blob through this single client, so the
	 * gateway route (`pdfUrl` = `/api/v1/invoices/{id}/pdf`) carries the caller's
	 * `Authorization: Bearer` header. A plain `<a href>` download would NOT send it,
	 * so the gateway would reject the request with 401; routing the fetch here also
	 * reuses the shared silent-renew / auth-redirect handling. `pdfUrl` is the
	 * gateway path the BFF composed on each {@link InvoiceSummary}; it is passed
	 * through verbatim (already `/api/v1`-prefixed and id-encoded by the BFF). The
	 * page turns the returned Blob into a browser download (object URL + anchor).
	 */
	downloadInvoicePdf(pdfUrl: string): Promise<Blob> {
		return this.requestBlob({
			method: 'GET',
			path: pdfUrl,
			headers: { Accept: 'application/pdf' }
		});
	}

	/** Tariffs + addons shaped for the onboarding wizard. */
	getOnboardingCatalog(): Promise<OnboardingCatalog> {
		return this.request<OnboardingCatalog>({
			method: 'GET',
			path: `${BFF_V1}/onboarding/catalog`
		});
	}

	/**
	 * Place an onboarding order. The write carries an `Idempotency-Key` (a fresh
	 * UUID is generated when one is not supplied) per the web-bff contract.
	 */
	placeOnboardingOrder(
		request: OnboardingOrderRequest,
		idempotencyKey: string = globalThis.crypto.randomUUID()
	): Promise<OnboardingOrderResult> {
		return this.request<OnboardingOrderResult>({
			method: 'POST',
			path: `${BFF_V1}/onboarding/order`,
			body: request,
			headers: { 'Idempotency-Key': idempotencyKey }
		});
	}

	// -- gateway thin-slice (ADR-022): not yet BFF-composed ------------------

	/**
	 * Fetch an order and its saga status, polled by the wizard's final step until
	 * a terminal outcome (activated / compensated). Targets the gateway directly
	 * because the web-bff does not compose an order-status read (web-bff contract).
	 */
	getOrderStatus(orderId: string): Promise<OrderStatus> {
		return this.request<OrderStatus>({
			method: 'GET',
			path: `${API_V1}/orders/${encodeURIComponent(orderId)}`
		});
	}

	/**
	 * Charge for an order via the payment-service (mock PSP). The mandatory
	 * `Idempotency-Key` header mirrors `request.paymentRequestId`, so a replayed
	 * submission returns the original outcome. Targets the gateway directly:
	 * payment is not BFF-composed (payment-service contract, ADR-022 thin slice).
	 */
	submitPayment(request: PaymentRequest): Promise<PaymentResult> {
		return this.request<PaymentResult>({
			method: 'POST',
			path: `${API_V1}/payments`,
			body: request,
			headers: { 'Idempotency-Key': request.paymentRequestId }
		});
	}

	// -- internals ----------------------------------------------------------

	private async request<T>(options: RequestOptions): Promise<T> {
		const response = await this.dispatch(options);

		if (response.status === 204) {
			return undefined as T;
		}

		return (await response.json()) as T;
	}

	/**
	 * Binary variant of {@link request}: shares the exact same URL construction,
	 * auth, and 401 handling via {@link dispatch}, but reads the validated response
	 * as a Blob (used for the authenticated invoice-PDF download). Kept on this
	 * single client so no second HTTP path is introduced.
	 */
	private async requestBlob(options: RequestOptions): Promise<Blob> {
		const response = await this.dispatch(options);
		return response.blob();
	}

	/**
	 * Build the URL, perform the outbound fetch, and apply the shared 401 policy,
	 * returning a validated (2xx) Response. The response body is read by the caller
	 * ({@link request} as JSON, {@link requestBlob} as a Blob).
	 */
	private async dispatch(options: RequestOptions): Promise<Response> {
		const url = joinUrl(this.baseUrl, options.path);

		let response = await this.execute(url, options);

		// Graceful 401 handling (16.3.3): the gateway rejects an expired/invalid
		// bearer with 401. Rather than ever surfacing a raw 401, attempt a single
		// silent token renewal and retry once; if renewal cannot recover the
		// session, redirect to /login (return-to preserved, 16.3.2) and still
		// reject so the caller stops. Only 401 triggers this - other errors pass
		// straight through to ApiError untouched.
		if (response.status === 401) {
			const renewed = await this.renewAccessToken();
			if (renewed) {
				response = await this.execute(url, options);
			}
			if (response.status === 401) {
				this.onAuthRedirect();
				throw new ApiError(response.status, url, await this.safeReadBody(response));
			}
		}

		if (!response.ok) {
			throw new ApiError(response.status, url, await this.safeReadBody(response));
		}

		return response;
	}

	/** Build headers (incl. a fresh bearer) and perform one outbound fetch. */
	private async execute(url: string, options: RequestOptions): Promise<Response> {
		const headers: Record<string, string> = {
			Accept: 'application/json',
			...options.headers
		};

		if (options.body !== undefined) {
			headers['Content-Type'] = 'application/json';
		}

		const token = await this.getAccessToken();
		if (token) {
			headers.Authorization = `Bearer ${token}`;
		}

		return this.fetchImpl(url, {
			method: options.method,
			headers,
			body: options.body === undefined ? undefined : JSON.stringify(options.body)
		});
	}

	private async safeReadBody(response: Response): Promise<unknown> {
		try {
			return await response.json();
		} catch {
			return null;
		}
	}
}

/** Create a BFF client with optional base URL, fetch, and auth-token seam. */
export function createApiClient(options: ApiClientOptions = {}): ApiClient {
	return new ApiClient(options);
}

/**
 * Default, ready-to-use client bound to the configured base URL. Import this in
 * pages/`load` functions; pass SvelteKit's scoped `fetch` where SSR needs it.
 */
export const api = createApiClient({
	getAccessToken: () => accessTokenProvider(),
	renewAccessToken: () => renewAccessTokenProvider(),
	onAuthRedirect: () => authRedirectHandler()
});
