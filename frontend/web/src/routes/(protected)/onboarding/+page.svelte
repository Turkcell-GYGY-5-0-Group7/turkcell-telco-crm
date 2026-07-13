<script lang="ts">
	// Onboarding wizard (subtask 16.4.2): register -> KYC -> plan -> review/order ->
	// polled activation, all within /onboarding. This page is the thin SvelteKit
	// adapter: it holds reactive state and orchestrates the single BFF client, while
	// ordering, validation, request shaping, status classification, and polling live
	// in the framework-agnostic $lib/onboarding modules (unit tested in Node).
	//
	// Placing the order is the ONLY write. Charging is event-driven (order.created.v1
	// -> payment-service charges -> payment.completed.v1 -> subscription activates ->
	// order FULFILLED, TELCO-CRM-MVP Section 9.2), so the wizard never calls
	// POST /api/v1/payments: that endpoint is payment-service's ADMIN-only manual
	// override and a browser call would double-charge the order. The final step
	// reflects the TRUE saga outcome by POLLING GET /api/v1/orders/{id} until a
	// terminal state (FULFILLED / CANCELLED / FAILED).
	//
	// Browser-only APIs (crypto, File, fetch) run only inside handlers / onMount, so
	// the adapter-node build (this group is ssr=false) does not break.
	import { onMount } from 'svelte';
	import {
		ApiError,
		api,
		type Addon,
		type KycDocumentType,
		type OnboardingCatalog,
		type OnboardingOrderResult,
		type Tariff
	} from '$lib/api/client';
	import {
		computeMonthlyTotal,
		isKycValid,
		isRegistrationValid,
		nextStep,
		prevStep,
		type KycForm,
		type RegistrationForm,
		type WizardStep
	} from '$lib/onboarding/wizard';
	import { buildRegisterOrderRequest, buildReuseOrderRequest } from '$lib/onboarding/order-request';
	import { pollOrderStatus, type PollResult } from '$lib/onboarding/order-status';
	import {
		recoveryActionFor,
		shouldReuseCustomer,
		type RecoveryAction
	} from '$lib/onboarding/recovery';
	import { shouldRefreshSessionAfterOnboarding } from '$lib/onboarding/link-state';
	import {
		MAX_KYC_FILE_BYTES,
		formatBytes,
		readFileAsBase64,
		validateKycFile
	} from '$lib/onboarding/file';
	import { renewSession } from '$lib/auth/oidc';
	import WizardProgress from './WizardProgress.svelte';
	import RegisterStep from './RegisterStep.svelte';
	import KycStep from './KycStep.svelte';
	import CatalogStep from './CatalogStep.svelte';
	import ReviewStep from './ReviewStep.svelte';
	import ResultStep from './ResultStep.svelte';

	// Exactly the BFF's CustomerRegistration fields; INDIVIDUAL is the only type the
	// MVP registers (customer-service validates identityNumber as a TCKN either way).
	const registration: RegistrationForm = $state({
		type: 'INDIVIDUAL',
		firstName: '',
		lastName: '',
		identityNumber: '',
		dateOfBirth: ''
	});

	const kyc: KycForm = $state({ type: 'ID_CARD', file: null });

	const DOCUMENT_LABELS: Record<KycDocumentType, string> = {
		ID_CARD: 'Identity card',
		PASSPORT: 'Passport'
	};

	let step = $state<WizardStep>('register');

	let catalog = $state<OnboardingCatalog | null>(null);
	let catalogLoading = $state(false);
	let catalogError = $state('');
	let selectedTariffCode = $state('');
	let selectedAddonCodes = $state<string[]>([]);

	let order = $state<OnboardingOrderResult | null>(null);
	let placing = $state(false);
	let placeError = $state('');

	// The customer the BFF registered. The order response does NOT carry it, so it is
	// learned from the polled order (order-service's OrderResponse.customerId) and then
	// used for the BFF's reuse path on a retry - re-registering the same TCKN would be
	// rejected by customer-service.
	let customerId = $state('');

	let polling = $state(false);
	let pollLiveStatus = $state('');
	let pollAttempts = $state(0);
	let pollResult = $state<PollResult | null>(null);
	let pollError = $state('');

	const selectedTariff = $derived<Tariff | null>(
		catalog?.tariffs.find((tariff) => tariff.code === selectedTariffCode) ?? null
	);
	const selectedAddons = $derived<Addon[]>(
		catalog?.addons.filter((addon) => selectedAddonCodes.includes(addon.code)) ?? []
	);
	const monthlyTotal = $derived(computeMonthlyTotal(selectedTariff, selectedAddons));
	const currency = $derived(selectedTariff?.currency ?? catalog?.tariffs[0]?.currency ?? 'TRY');

	// Load the composed catalog once, up front, so it is ready at the plan step.
	onMount(() => {
		void loadCatalog();
	});

	async function loadCatalog() {
		catalogLoading = true;
		catalogError = '';
		try {
			catalog = await api.getOnboardingCatalog();
		} catch (error) {
			catalogError = describeError(error, 'Could not load the catalog.');
		} finally {
			catalogLoading = false;
		}
	}

	function onSelectTariff(tariffCode: string) {
		selectedTariffCode = tariffCode;
		// Addons are bound to a tariff (AddonOption.tariffCode); drop any that no longer apply.
		selectedAddonCodes = selectedAddonCodes.filter((code) =>
			catalog?.addons.some((addon) => addon.code === code && addon.tariffCode === tariffCode)
		);
	}

	function onToggleAddon(addonCode: string) {
		selectedAddonCodes = selectedAddonCodes.includes(addonCode)
			? selectedAddonCodes.filter((code) => code !== addonCode)
			: [...selectedAddonCodes, addonCode];
	}

	function onKycType(type: KycDocumentType) {
		kyc.type = type;
	}

	function onKycFile(file: File | null) {
		kyc.file = file;
	}

	const kycFilename = $derived(kyc.file?.name ?? '');
	const reusingCustomer = $derived(shouldReuseCustomer(customerId));

	const canAdvance = $derived.by(() => {
		switch (step) {
			case 'register':
				return isRegistrationValid(registration);
			case 'kyc':
				// Already-registered customers re-order through the reuse path: the BFF skips
				// register + KYC entirely, so a fresh document is not required.
				return reusingCustomer || isKycValid(kyc);
			case 'catalog':
				return selectedTariffCode !== '' && !catalogLoading && catalog !== null;
			default:
				return true;
		}
	});

	function goNext() {
		if (canAdvance) step = nextStep(step);
	}

	function goBack() {
		step = prevStep(step);
	}

	/**
	 * Place the order through the BFF and immediately start polling for the saga's
	 * terminal outcome. First placement takes the REGISTER path (customer + KYC
	 * document); a retry after a failure takes the REUSE path (customerId only). Each
	 * placement carries a FRESH Idempotency-Key so a retry is a genuinely new order
	 * rather than a replay of the failed one (order-service returns the original order
	 * for a repeated key).
	 */
	async function placeOrder() {
		if (placing) return;
		placing = true;
		placeError = '';
		try {
			const request = reusingCustomer
				? buildReuseOrderRequest({
						customerId,
						tariffCode: selectedTariffCode,
						addonCodes: selectedAddonCodes
					})
				: buildRegisterOrderRequest({
						registration,
						kycDocument: await readKycDocument(),
						tariffCode: selectedTariffCode,
						addonCodes: selectedAddonCodes
					});

			order = await api.placeOnboardingOrder(request, globalThis.crypto.randomUUID());

			// No payment call: the charge is triggered server-side by order.created.v1.
			// The wizard's job from here is to report the real outcome, so it polls.
			step = 'result';
			await startPolling(order.orderId);
		} catch (error) {
			placeError = describeError(error, 'Could not place the order.');
		} finally {
			placing = false;
		}
	}

	/**
	 * Read the captured file into the BFF's KycDocument shape (type/fileName/contentType/content).
	 * The size/type policy is re-applied here as the last gate before the bytes go on the wire:
	 * the KYC step already blocks an oversize document, and this makes it impossible for any other
	 * path (a restored draft, a back-navigation) to slip one past.
	 */
	async function readKycDocument() {
		const file = kyc.file;
		const check = validateKycFile(file);
		if (!check.valid) {
			throw new Error(check.message);
		}
		if (!file) {
			throw new Error('A KYC document is required to register.');
		}
		return {
			type: kyc.type,
			fileName: file.name,
			contentType: file.type || 'application/octet-stream',
			content: await readFileAsBase64(file)
		};
	}

	// -- failure/compensation recovery (16.4.3) ------------------------------

	// The honest recovery path for a terminal failure, derived from the polled saga
	// status. A CANCELLED/FAILED order is terminal and already refunded, and the browser
	// has no payment call to retry, so the only real path is a NEW order for the same
	// customer. Non-failures offer nothing.
	const recoveryAction = $derived<RecoveryAction>(
		pollResult && pollResult.outcome === 'failed' ? recoveryActionFor(pollResult.status) : 'none'
	);

	function resetPollState() {
		polling = false;
		pollLiveStatus = '';
		pollAttempts = 0;
		pollResult = null;
		pollError = '';
	}

	/**
	 * Re-place the order after a failed saga. The customer (and their KYC document)
	 * already exist, so the wizard routes back to review, where placing the order takes
	 * the BFF's reuse path with a fresh Idempotency-Key.
	 */
	function retryOrder() {
		order = null;
		placeError = '';
		resetPollState();
		step = 'review';
	}

	/**
	 * Full restart: keep the typed details and plan for convenience but drop the failed
	 * order/poll so the wizard places a brand-new order from the top. The customer id is
	 * kept when one is known, so a restart still reuses the existing registration.
	 */
	function startOver() {
		order = null;
		placeError = '';
		resetPollState();
		step = 'register';
	}

	async function startPolling(orderId: string) {
		polling = true;
		pollError = '';
		pollResult = null;
		pollLiveStatus = '';
		pollAttempts = 0;
		try {
			pollResult = await pollOrderStatus((id) => api.getOrderStatus(id), orderId, {
				onTick: ({ status, attempts }) => {
					pollLiveStatus = status;
					pollAttempts = attempts;
				}
			});
			// order-service is the only source that names the customer behind this order.
			if (pollResult.customerId) {
				customerId = pollResult.customerId;
			}

			// STALE TOKEN: the access token in hand was issued BEFORE this customer
			// existed, so it still carries `customerId: null` - and every BFF account
			// read (/bff/v1/home|account|invoices) would be refused 403 by the
			// self-scoping guard even though onboarding just succeeded. identity-service
			// mints the claim once it consumes `customer.registered.v1`, so a fresh token
			// carries the link: renew now, through the ONE existing silent-renew path, so
			// the dashboard the user lands on is loaded with a linked identity. Best
			// effort - `renewSession` never throws, and if it yields a token that still
			// has no link, the account pages fall back to the onboarding CTA (they retry
			// a renew once themselves) instead of looping.
			if (shouldRefreshSessionAfterOnboarding(pollResult)) {
				await renewSession();
			}
		} catch (error) {
			pollError = describeError(error, 'Could not confirm activation.');
		} finally {
			polling = false;
		}
	}

	/**
	 * Surface the SERVER's own message when it sent one (ADR-015 error envelope) - a
	 * rejected TCKN, for example, arrives as customer-service's 400 relayed by the BFF.
	 * Inventing a friendlier message would hide why the request actually failed.
	 *
	 * A `fetch` rejection is a different animal: it is NOT an `ApiError` (there is no
	 * response at all), it is a bare `TypeError` whose message is the browser's internal
	 * "Failed to fetch"/"NetworkError when attempting to fetch resource". Showing that
	 * text to a user is meaningless, so it is translated into the two things that actually
	 * cause it here: the request never reached the server, or it was cut off mid-upload
	 * (a document too large for the server to accept). See {@link describeNetworkFailure}.
	 */
	function describeError(error: unknown, fallback: string): string {
		if (error instanceof ApiError) {
			const detail = error.serverMessage;
			return detail
				? `${fallback} ${detail} (HTTP ${error.status})`
				: `${fallback} (HTTP ${error.status})`;
		}
		if (isNetworkFailure(error)) {
			return `${fallback} ${describeNetworkFailure()}`;
		}
		if (error instanceof Error && error.message) {
			return `${fallback} ${error.message}`;
		}
		return fallback;
	}

	/**
	 * True for a transport-level failure: `fetch` rejects with a `TypeError` (not an
	 * `ApiError`) when the connection is refused, dropped, or reset - which is exactly what
	 * a server-aborted oversize upload looks like from the browser.
	 */
	function isNetworkFailure(error: unknown): boolean {
		return error instanceof TypeError;
	}

	/** The human-readable cause of a transport failure while placing the order. */
	function describeNetworkFailure(): string {
		return (
			`The server could not be reached, so nothing was submitted. ` +
			`This usually means the connection dropped, or the identity document was too large to upload ` +
			`(the limit is ${formatBytes(MAX_KYC_FILE_BYTES)}). Check your connection, re-attach a smaller ` +
			`document if needed, and try again.`
		);
	}

	const showBack = $derived(step === 'kyc' || step === 'catalog' || step === 'review');
</script>

<section class="page">
	<h1>Onboarding</h1>
	<WizardProgress current={step} />

	<div class="card">
		{#if step === 'register'}
			<RegisterStep form={registration} />
		{:else if step === 'kyc'}
			<KycStep
				documentType={kyc.type}
				file={kyc.file}
				onTypeSelected={onKycType}
				onFileSelected={onKycFile}
			/>
		{:else if step === 'catalog'}
			<CatalogStep
				{catalog}
				loading={catalogLoading}
				error={catalogError}
				{selectedTariffCode}
				{selectedAddonCodes}
				total={monthlyTotal}
				{currency}
				{onSelectTariff}
				{onToggleAddon}
			/>
		{:else if step === 'review'}
			<ReviewStep
				form={registration}
				documentLabel={DOCUMENT_LABELS[kyc.type]}
				filename={kycFilename}
				tariff={selectedTariff}
				addons={selectedAddons}
				total={monthlyTotal}
				{currency}
				{placing}
				{reusingCustomer}
				error={placeError}
			/>
		{:else if step === 'result'}
			<ResultStep
				{polling}
				liveStatus={pollLiveStatus}
				attempts={pollAttempts}
				result={pollResult}
				error={pollError}
				recovery={recoveryAction}
				onRetryOrder={retryOrder}
				onStartOver={startOver}
			/>
		{/if}
	</div>

	<div class="actions">
		{#if showBack}
			<button type="button" class="secondary" onclick={goBack} disabled={placing}> Back </button>
		{/if}

		{#if step === 'register' || step === 'kyc' || step === 'catalog'}
			<button type="button" class="primary" onclick={goNext} disabled={!canAdvance}>
				Continue
			</button>
		{:else if step === 'review'}
			<button type="button" class="primary" onclick={placeOrder} disabled={placing}>
				Place order
			</button>
		{/if}
	</div>
</section>

<style>
	.page {
		max-width: 40rem;
	}

	h1 {
		margin: 0 0 1rem;
		font-size: 1.5rem;
	}

	.card {
		background: #ffffff;
		border: 1px solid #e5e7eb;
		border-radius: 0.75rem;
		padding: 1.5rem;
	}

	.actions {
		display: flex;
		gap: 0.75rem;
		justify-content: flex-end;
		margin-top: 1.25rem;
	}

	button {
		font: inherit;
		padding: 0.5rem 1.1rem;
		border-radius: 0.375rem;
		cursor: pointer;
	}

	.primary {
		border: 1px solid #16213e;
		background: #16213e;
		color: #ffffff;
	}

	.secondary {
		border: 1px solid #d1d5db;
		background: #ffffff;
		color: #374151;
	}

	button:disabled {
		opacity: 0.6;
		cursor: default;
	}
</style>
