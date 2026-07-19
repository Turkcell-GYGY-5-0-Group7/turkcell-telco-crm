// Typed Backend-for-Frontend (BFF) API client for the Telco CRM web app.
//
// ADR-022 / ADR-011 Section 2: the browser never calls a domain service
// directly. This module is the SINGLE place in the frontend that constructs
// outbound request URLs. Composed reads target the BFF surface (`/bff/v1/**`).
// Reads and writes the web-bff does not compose fall back to the API GATEWAY
// thin-slice surface (`/api/v1/**`) under ADR-022's thin-slice allowance. This
// started as one call (order-status polling for the onboarding saga) and now
// covers the broader self-service and CRM-console surface the expanded frontend
// needs (usage, tickets, notifications, orders, subscriptions, catalog, customer
// reads) - every one of these is a real gateway endpoint the caller is authorized
// for, and each is re-checked server-side by the owning service's RBAC and
// ownership rules (the browser decoding its own token only decides what to SHOW,
// never what it is ALLOWED to do). Both surfaces resolve against the same
// configurable gateway base URL; a domain-service host/port is never targeted,
// and this remains the only HTTP path in the frontend.
//
// The browser does NOT submit payments. Charging is event-driven: order-service
// publishes `order.created.v1` and payment-service's inbox consumer charges from
// it (docs/product/TELCO-CRM-MVP.md Section 9.2). `POST /api/v1/payments` is a
// manual ADMIN-only override on payment-service (`@PreAuthorize("hasRole('ADMIN')")`),
// so a browser call would be both a 403 for a SUBSCRIBER and a second charge on
// the same order. Placing the order is the ONLY write the wizard performs.
//
// Every type below is derived from the web-bff's actual Java DTOs
// (microservices/web-bff/src/main/java/com/telco/webbff/dto/), not from prose:
// that mismatch is exactly what broke the wizard once already.
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
 * API gateway version prefix. Used only for the single thin-slice call the web-bff
 * does not compose (order-status polling); see the module header and ADR-022.
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

	/**
	 * The server's own error message, when the body is a platform `ApiResult`
	 * failure (ADR-015: `{ success: false, error: { code, message, details } }`).
	 * The BFF renders every downstream rejection through that envelope - including
	 * a rejected TCKN, which customer-service returns as a 400 and the BFF's
	 * GatewayClient translates into a `ValidationException`. Surfacing this text
	 * lets the wizard tell the user the truth instead of an invented message.
	 * Returns null when the body carries no usable message.
	 */
	get serverMessage(): string | null {
		const body = this.body as { error?: { message?: unknown } } | null | undefined;
		const message = body?.error?.message;
		return typeof message === 'string' && message.trim().length > 0 ? message : null;
	}
}

/**
 * The platform's universal response envelope (ADR-015 `ApiResult<T>`). Domain
 * services on the gateway surface (`/api/v1/**`) ALWAYS wrap their payload in it;
 * the BFF surface (`/bff/v1/**`) deliberately does not (documented ADR-015
 * exception, web-bff contract Notes). So only the gateway thin-slice read below
 * has to unwrap it.
 */
interface ApiResult<T> {
	success: boolean;
	data?: T;
	error?: { code: string; message: string; details?: Record<string, unknown> };
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

/**
 * One tariff in the onboarding catalog. Mirrors the BFF `TariffOption` record
 * EXACTLY: the identifier the order request carries is the tariff CODE (e.g.
 * `POSTPAID-M`), never an id - the BFF resolves the code to the tariff's UUID
 * server-side before placing the order. `description` carries the tariff's target
 * segment as composed by the BFF.
 */
export interface Tariff {
	code: string;
	name: string;
	description: string;
	monthlyPrice: number;
	currency: string;
}

/**
 * One addon in the onboarding catalog. Mirrors the BFF `AddonOption` record: the
 * identifier is the addon CODE, the price field is `price` (not `monthlyPrice`),
 * and `tariffCode` binds the addon to the tariff it extends.
 */
export interface Addon {
	code: string;
	name: string;
	tariffCode: string;
	price: number;
	currency: string;
}

/** GET /bff/v1/onboarding/catalog (BFF `OnboardingCatalogResponse`). */
export interface OnboardingCatalog {
	tariffs: Tariff[];
	addons: Addon[];
}

/** Customer classification (customer-service `CustomerType`). */
export type CustomerType = 'INDIVIDUAL' | 'CORPORATE';

/**
 * Registration details relayed by the BFF to customer-service
 * (`POST /api/v1/customers`). Mirrors the BFF `CustomerRegistration` record, which
 * in turn mirrors `RegisterCustomerRequest`: identity is a TCKN whose CHECKSUM
 * customer-service validates (`@ValidTckn`), and `dateOfBirth` must be in the past
 * (`@Past`). No email/phone exists in this contract - the domain does not capture
 * them at registration.
 */
export interface CustomerRegistration {
	type: CustomerType;
	firstName: string;
	lastName: string;
	/** Turkish national identity number (TCKN); checksum-validated server-side. */
	identityNumber: string;
	/** ISO date (`YYYY-MM-DD`), serialized as a Java `LocalDate`. */
	dateOfBirth: string;
}

/** KYC document type accepted by customer-service (`DocumentType`). */
export type KycDocumentType = 'ID_CARD' | 'PASSPORT';

/**
 * A KYC identity document captured in the wizard and forwarded to the BFF, which
 * decodes it and relays it as a multipart upload to customer-service
 * (`POST /api/v1/customers/{id}/documents`). Mirrors the BFF `KycDocument` record:
 * the field names are `type` / `fileName` / `contentType` / `content` (base64), so
 * the bytes ride the single JSON order call.
 */
export interface KycDocumentPayload {
	type: KycDocumentType;
	fileName: string;
	contentType: string;
	/** Base64-encoded document bytes (no data-URL prefix). */
	content: string;
}

/**
 * POST /bff/v1/onboarding/order request body. Mirrors the BFF
 * `OnboardingOrderRequest` record exactly.
 *
 * Two mutually exclusive identity paths, enforced by the BFF's composition service:
 * - REGISTER: supply `customer` + `kycDocument` (the BFF registers the customer and
 *   uploads the document, then places the order).
 * - REUSE: supply `customerId` (the BFF skips register/KYC entirely). Used when the
 *   wizard re-places an order for a customer it already registered.
 *
 * `tariffCode` is mandatory (`@NotBlank`); `addonCodes` may be empty.
 */
export interface OnboardingOrderRequest {
	/** Reuse path: an already-registered customer. Omit to register a new one. */
	customerId?: string;
	/** Register path: required when no `customerId` is supplied. */
	customer?: CustomerRegistration;
	/** Register path: required when no `customerId` is supplied. */
	kycDocument?: KycDocumentPayload;
	tariffCode: string;
	addonCodes: string[];
}

/**
 * POST /bff/v1/onboarding/order response. Mirrors the BFF `OnboardingOrderResponse`
 * record: order id, saga status, and the echoed idempotency key. It carries NO
 * customerId - the wizard must not depend on one here (that assumption is what broke
 * the flow); the polled order (`GET /api/v1/orders/{id}`) is the real source.
 */
export interface OnboardingOrderResult {
	orderId: string;
	status: string;
	idempotencyKey: string;
}

/**
 * The order-service read model behind `GET /api/v1/orders/{id}`, wrapped in
 * `ApiResult<T>` on the wire. Only the fields the wizard uses are declared.
 */
interface GatewayOrderResponse {
	/** order-service names it `id`, not `orderId`. */
	id: string;
	customerId: string;
	status: string;
}

/**
 * The order + saga status polled by the wizard, unwrapped from `ApiResult<T>` and
 * normalized. `customerId` is the ONLY place the wizard can honestly learn which
 * customer the BFF registered (the order response does not carry it); it feeds the
 * BFF's `customerId` reuse path when an order has to be placed again.
 */
export interface OrderStatus {
	orderId: string;
	customerId: string;
	status: string;
}

// ---------------------------------------------------------------------------
// Gateway thin-slice types (derived from the domain services' Java DTOs, which
// answer wrapped in ApiResult<T>). These back the expanded self-service and
// CRM-console pages the BFF does not compose. Each mirrors a real response record
// field-for-field; see the owning service under microservices/<svc>/.
// ---------------------------------------------------------------------------

/** usage-service `UsageType`. */
export type UsageType = 'VOICE' | 'DATA' | 'SMS';

/** Cursor-based page envelope (platform `CursorPage<T>`, ADR-015). */
export interface CursorPage<T> {
	content: T[];
	/** Opaque cursor for the next page; null when there is none. */
	nextCursor: string | null;
	hasNext: boolean;
	limit: number;
}

/** Offset-based page envelope (platform `PageResult<T>`, ADR-015). */
export interface PageResult<T> {
	content: T[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}

/** usage-service `QuotaResponse` - remaining allowance for the current period. */
export interface Quota {
	quotaId: string;
	subscriptionId: string;
	periodStart: string;
	periodEnd: string;
	minutesTotal: number;
	smsTotal: number;
	mbTotal: number;
	minutesRemaining: number;
	smsRemaining: number;
	mbRemaining: number;
}

/** usage-service `UsageHistoryItem` - one CDR. */
export interface UsageHistoryItem {
	id: string;
	subscriptionId: string;
	type: UsageType;
	quantity: number;
	overage: boolean;
	cdrRef: string;
	recordedAt: string;
}

/** ticket-service `TicketCommentResponse`. */
export interface TicketComment {
	id: string;
	authorId: string;
	body: string;
	createdAt: string;
}

/** ticket-service `TicketResponse`. */
export interface Ticket {
	id: string;
	customerId: string;
	category: string;
	priority: string;
	status: string;
	assignedTeam: string | null;
	subject: string;
	slaDueAt: string | null;
	slaBreached: boolean;
	createdAt: string;
	resolvedAt: string | null;
	comments: TicketComment[];
}

/** ticket-service `OpenTicketRequest`. */
export interface OpenTicketRequest {
	category: string;
	priority: string;
	subject: string;
}

/** notification-service `NotificationResponse`. */
export interface NotificationRecord {
	id: string;
	userId: string;
	templateCode: string;
	channel: string;
	status: string;
	createdAt: string;
	sentAt: string | null;
}

/** notification-service `CommunicationPreference` (Mongo document, Jackson-shaped). */
export interface CommunicationPreference {
	id: string;
	userId: string;
	channel: string;
	optedIn: boolean;
	updatedAt: string;
}

/** order-service `OrderItemResponse`. */
export interface OrderItem {
	id: string;
	tariffId: string;
	tariffCode: string;
	tariffVersion: number;
	tariffName: string;
	unitPrice: number;
	quantity: number;
}

/** order-service `OrderResponse`. */
export interface Order {
	id: string;
	customerId: string;
	status: string;
	idempotencyKey: string;
	totalAmount: number;
	items: OrderItem[];
	createdAt: string;
	updatedAt: string;
}

/** subscription-service `SubscriptionResponse`. */
export interface SubscriptionDetail {
	id: string;
	customerId: string;
	msisdn: string;
	tariffCode: string;
	tariffVersion: number;
	status: string;
	activatedAt: string | null;
	terminatedAt: string | null;
	createdAt: string;
}

/** billing-service `InvoiceResponse` (a single invoice with lines). */
export interface InvoiceDetail {
	id: string;
	customerId: string;
	subscriptionId: string;
	periodStart: string;
	periodEnd: string;
	subTotal: number;
	tax: number;
	grandTotal: number;
	currency: string;
	status: string;
	dueDate: string;
	issuedAt: string | null;
	pdfRef: string | null;
	createdAt: string;
	lines?: InvoiceLine[];
}

/** billing-service `InvoiceLineResponse`. */
export interface InvoiceLine {
	description: string;
	amount: number;
}

/** customer-service `CustomerResponse` (identity number is masked server-side). */
export interface Customer {
	id: string;
	type: string;
	firstName: string;
	lastName: string;
	identityNumberMasked: string;
	dateOfBirth: string;
	status: string;
	createdAt: string;
}

/** customer-service `UpdateCustomerRequest` - only these three fields are mutable. */
export interface UpdateCustomerRequest {
	firstName: string;
	lastName: string;
	/** ISO date (`YYYY-MM-DD`). */
	dateOfBirth: string;
}

/** product-catalog-service `TariffResponse`. */
export interface CatalogTariff {
	id: string;
	code: string;
	name: string;
	type: string;
	status: string;
	monthlyFee: number;
	currency: string;
	minutesIncluded: number;
	smsIncluded: number;
	dataMbIncluded: number;
	targetSegment: string;
	effectiveFrom: string;
	effectiveTo: string | null;
	version: number;
	createdAt: string;
	updatedAt: string;
}

/** product-catalog-service `AddonResponse`. */
export interface CatalogAddon {
	id: string;
	code: string;
	name: string;
	price: number;
	currency: string;
	type: string;
	validityDays: number;
	status: string;
	createdAt: string;
}

/** payment-service `PaymentResponse`. */
export interface Payment {
	id: string;
	orderId: string;
	customerId: string;
	amount: number;
	status: string;
	paymentRequestId: string;
	invoiceId: string | null;
	createdAt: string;
	updatedAt: string;
	attemptCount: number;
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

	// -- gateway thin-slice (ADR-022): not BFF-composed -----------------------

	/**
	 * Fetch an order and its saga status, polled by the wizard's final step until a
	 * terminal outcome (FULFILLED / CANCELLED / FAILED). Targets the gateway directly
	 * because the web-bff composes no order-status read (web-bff contract).
	 *
	 * Unlike the BFF surface, order-service answers with the platform envelope
	 * `ApiResult<OrderResponse>` (ADR-015) and names the identifier `id`. Both are
	 * normalized here so callers see a flat {@link OrderStatus} - this is the single
	 * place that knows about the envelope.
	 */
	async getOrderStatus(orderId: string): Promise<OrderStatus> {
		const order = await this.requestGateway<GatewayOrderResponse>({
			method: 'GET',
			path: `${API_V1}/orders/${encodeURIComponent(orderId)}`
		});
		return { orderId: order.id, customerId: order.customerId, status: order.status };
	}

	// -- gateway thin-slice (ADR-022): expanded self-service + CRM surface ----
	//
	// Everything below is a real `/api/v1` endpoint the caller is authorized for,
	// answered with the platform envelope `ApiResult<T>` and unwrapped by the
	// shared {@link ApiClient.requestGateway}. The owning service enforces RBAC and
	// per-record ownership; the frontend only reaches endpoints scoped to the
	// signed-in user (self-service) or gated in the UI to a staff role (CRM), and
	// the gateway rejects anything else. See the module header.

	/** Active quota for one subscription (self-scoped or ADMIN). */
	getQuota(subscriptionId: string): Promise<Quota> {
		return this.requestGateway<Quota>({
			method: 'GET',
			path: `${API_V1}/usage/subscriptions/${encodeURIComponent(subscriptionId)}/quota`
		});
	}

	/** Cursor-paginated CDR history for one subscription within a time window. */
	getUsageHistory(
		subscriptionId: string,
		fromIso: string,
		toIso: string,
		cursor?: string,
		limit?: number
	): Promise<CursorPage<UsageHistoryItem>> {
		const params = new URLSearchParams({ from: fromIso, to: toIso });
		if (cursor) params.set('cursor', cursor);
		if (limit !== undefined) params.set('limit', String(limit));
		return this.requestGateway<CursorPage<UsageHistoryItem>>({
			method: 'GET',
			path: `${API_V1}/usage/subscriptions/${encodeURIComponent(subscriptionId)}/history?${params.toString()}`
		});
	}

	/** Open a support ticket for the caller's own linked customer. Returns the new id. */
	openTicket(request: OpenTicketRequest): Promise<string> {
		return this.requestGateway<string>({
			method: 'POST',
			path: `${API_V1}/tickets`,
			body: request
		});
	}

	/** Read a single ticket (owner or ADMIN), including its comment thread. */
	getTicket(ticketId: string): Promise<Ticket> {
		return this.requestGateway<Ticket>({
			method: 'GET',
			path: `${API_V1}/tickets/${encodeURIComponent(ticketId)}`
		});
	}

	/** Append a comment to a ticket. Returns the new comment id. */
	addTicketComment(ticketId: string, body: string): Promise<string> {
		return this.requestGateway<string>({
			method: 'POST',
			path: `${API_V1}/tickets/${encodeURIComponent(ticketId)}/comments`,
			body: { body }
		});
	}

	/** Assign a ticket to a support team (ADMIN). */
	assignTicket(ticketId: string, team: string): Promise<void> {
		return this.requestGatewayVoid({
			method: 'POST',
			path: `${API_V1}/tickets/${encodeURIComponent(ticketId)}/assign`,
			body: { team }
		});
	}

	/** Resolve a ticket (ADMIN). */
	resolveTicket(ticketId: string): Promise<void> {
		return this.requestGatewayVoid({
			method: 'POST',
			path: `${API_V1}/tickets/${encodeURIComponent(ticketId)}/resolve`
		});
	}

	/**
	 * A page of the caller's notification history. The path segment is the caller's
	 * own customerId (the notification service keys history by customerId and the
	 * gateway's SpEL rule requires the path id to equal the caller's claim).
	 */
	getNotificationHistory(
		customerId: string,
		page?: number,
		size?: number
	): Promise<PageResult<NotificationRecord>> {
		const params = new URLSearchParams();
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		const query = params.toString();
		const base = `${API_V1}/notifications/users/${encodeURIComponent(customerId)}/history`;
		return this.requestGateway<PageResult<NotificationRecord>>({
			method: 'GET',
			path: query ? `${base}?${query}` : base
		});
	}

	/** The caller's communication (channel opt-in) preferences. */
	getNotificationPreferences(customerId: string): Promise<CommunicationPreference[]> {
		return this.requestGateway<CommunicationPreference[]>({
			method: 'GET',
			path: `${API_V1}/notifications/users/${encodeURIComponent(customerId)}/preferences`
		});
	}

	/** Set the opt-in flag for one channel and return the stored preference. */
	updateNotificationPreference(
		customerId: string,
		channel: string,
		optedIn: boolean
	): Promise<CommunicationPreference> {
		return this.requestGateway<CommunicationPreference>({
			method: 'PUT',
			path: `${API_V1}/notifications/users/${encodeURIComponent(customerId)}/preferences/${encodeURIComponent(channel)}`,
			body: { optedIn }
		});
	}

	/** A page of the caller's own orders. */
	listMyOrders(customerId: string, page?: number, size?: number): Promise<PageResult<Order>> {
		const params = new URLSearchParams();
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		const query = params.toString();
		const base = `${API_V1}/orders/customer/${encodeURIComponent(customerId)}`;
		return this.requestGateway<PageResult<Order>>({
			method: 'GET',
			path: query ? `${base}?${query}` : base
		});
	}

	/** Read a single order (owner or ADMIN). */
	getOrder(orderId: string): Promise<Order> {
		return this.requestGateway<Order>({
			method: 'GET',
			path: `${API_V1}/orders/${encodeURIComponent(orderId)}`
		});
	}

	/**
	 * Cancel an order (owner or ADMIN). Only PENDING/CONFIRMED orders are
	 * cancellable; a rejected state transition surfaces as an {@link ApiError} whose
	 * {@link ApiError.serverMessage} carries the reason. Uses HTTP DELETE.
	 */
	cancelOrder(orderId: string, reason?: string): Promise<Order> {
		const params = new URLSearchParams();
		if (reason) params.set('reason', reason);
		const query = params.toString();
		const base = `${API_V1}/orders/${encodeURIComponent(orderId)}`;
		return this.requestGateway<Order>({
			method: 'DELETE',
			path: query ? `${base}?${query}` : base
		});
	}

	/** A page of subscriptions for a customer (own, or any for ADMIN). */
	listSubscriptions(
		customerId: string,
		page?: number,
		size?: number
	): Promise<PageResult<SubscriptionDetail>> {
		const params = new URLSearchParams({ customerId });
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		return this.requestGateway<PageResult<SubscriptionDetail>>({
			method: 'GET',
			path: `${API_V1}/subscriptions?${params.toString()}`
		});
	}

	/** Read a single subscription (owner or ADMIN). */
	getSubscription(subscriptionId: string): Promise<SubscriptionDetail> {
		return this.requestGateway<SubscriptionDetail>({
			method: 'GET',
			path: `${API_V1}/subscriptions/${encodeURIComponent(subscriptionId)}`
		});
	}

	/** Suspend a subscription (ADMIN in this UI). Returns the affected id. */
	suspendSubscription(subscriptionId: string, reason?: string): Promise<string> {
		return this.requestGateway<string>({
			method: 'POST',
			path: `${API_V1}/subscriptions/${encodeURIComponent(subscriptionId)}/suspend`,
			body: reason ? { reason } : undefined
		});
	}

	/** Reactivate a suspended subscription (ADMIN in this UI). */
	reactivateSubscription(subscriptionId: string): Promise<string> {
		return this.requestGateway<string>({
			method: 'POST',
			path: `${API_V1}/subscriptions/${encodeURIComponent(subscriptionId)}/reactivate`
		});
	}

	/** Terminate a subscription (ADMIN in this UI). */
	terminateSubscription(subscriptionId: string): Promise<string> {
		return this.requestGateway<string>({
			method: 'POST',
			path: `${API_V1}/subscriptions/${encodeURIComponent(subscriptionId)}/terminate`
		});
	}

	/** Read a single invoice with its line items (owner or ADMIN). */
	getInvoiceById(invoiceId: string): Promise<InvoiceDetail> {
		return this.requestGateway<InvoiceDetail>({
			method: 'GET',
			path: `${API_V1}/invoices/${encodeURIComponent(invoiceId)}`
		});
	}

	/** A page of a customer's invoices (owner or ADMIN). */
	listInvoicesForCustomer(
		customerId: string,
		page?: number,
		size?: number
	): Promise<PageResult<InvoiceDetail>> {
		const params = new URLSearchParams({ customerId });
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		return this.requestGateway<PageResult<InvoiceDetail>>({
			method: 'GET',
			path: `${API_V1}/invoices?${params.toString()}`
		});
	}

	/** A page of customers (ADMIN / CALL_CENTER_AGENT). */
	listCustomers(page?: number, size?: number): Promise<PageResult<Customer>> {
		const params = new URLSearchParams();
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		const query = params.toString();
		const base = `${API_V1}/customers`;
		return this.requestGateway<PageResult<Customer>>({
			method: 'GET',
			path: query ? `${base}?${query}` : base
		});
	}

	/** Read a single customer (staff, or the customer's own record). */
	getCustomer(customerId: string): Promise<Customer> {
		return this.requestGateway<Customer>({
			method: 'GET',
			path: `${API_V1}/customers/${encodeURIComponent(customerId)}`
		});
	}

	/** Update a customer's editable profile fields (staff, or the owner). */
	updateCustomer(customerId: string, request: UpdateCustomerRequest): Promise<Customer> {
		return this.requestGateway<Customer>({
			method: 'PUT',
			path: `${API_V1}/customers/${encodeURIComponent(customerId)}`,
			body: request
		});
	}

	/**
	 * A page of the tariff catalog (any authenticated caller). product-catalog-service
	 * answers with a paginated `PageResult`, so this returns the page and the caller
	 * reads `.content`.
	 */
	listTariffs(page?: number, size?: number): Promise<PageResult<CatalogTariff>> {
		const params = new URLSearchParams();
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		const query = params.toString();
		const base = `${API_V1}/tariffs`;
		return this.requestGateway<PageResult<CatalogTariff>>({
			method: 'GET',
			path: query ? `${base}?${query}` : base
		});
	}

	/**
	 * A page of the addon catalog, optionally filtered to one tariff (any
	 * authenticated caller). Also a paginated `PageResult`. Note addons are keyed to a
	 * tariff, so an unfiltered call returns whatever the service pages by default.
	 */
	listAddons(tariffCode?: string, page?: number, size?: number): Promise<PageResult<CatalogAddon>> {
		const params = new URLSearchParams();
		if (tariffCode) params.set('tariffCode', tariffCode);
		if (page !== undefined) params.set('page', String(page));
		if (size !== undefined) params.set('size', String(size));
		const query = params.toString();
		const base = `${API_V1}/addons`;
		return this.requestGateway<PageResult<CatalogAddon>>({
			method: 'GET',
			path: query ? `${base}?${query}` : base
		});
	}

	/**
	 * The payment recorded for an order, when one exists. Best-effort: charging is
	 * event-driven, so an order may have no payment yet (the caller treats a thrown
	 * {@link ApiError} as "no payment information").
	 */
	getPaymentByOrder(orderId: string): Promise<Payment> {
		return this.requestGateway<Payment>({
			method: 'GET',
			path: `${API_V1}/payments/order/${encodeURIComponent(orderId)}`
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
	 * Gateway thin-slice variant of {@link request}: shares the exact same URL
	 * construction, auth, and 401 handling, then unwraps the platform envelope
	 * `ApiResult<T>` that every `/api/v1` service returns (ADR-015). A 2xx that is
	 * not a successful envelope means the gateway answered with a shape we cannot
	 * trust, so it fails loudly (502) rather than returning a fabricated value. This
	 * is the SINGLE place that knows about the envelope; all gateway methods above
	 * route through it and receive a flat payload.
	 */
	private async requestGateway<T>(options: RequestOptions): Promise<T> {
		const envelope = await this.request<ApiResult<T>>(options);
		if (!envelope?.success || envelope.data === undefined || envelope.data === null) {
			throw new ApiError(502, joinUrl(this.baseUrl, options.path), envelope);
		}
		return envelope.data;
	}

	/**
	 * Gateway variant for endpoints that answer with `ApiResult<Unit>` (no
	 * meaningful payload, e.g. ticket assign/resolve). Shares dispatch/401 handling
	 * and asserts only that the envelope reports success.
	 */
	private async requestGatewayVoid(options: RequestOptions): Promise<void> {
		const envelope = await this.request<ApiResult<unknown>>(options);
		if (!envelope?.success) {
			throw new ApiError(502, joinUrl(this.baseUrl, options.path), envelope);
		}
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
