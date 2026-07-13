<script lang="ts">
	// Onboarding wizard (subtask 16.4.2): register -> KYC -> plan -> review/order
	// -> pay -> polled activation, all within /onboarding. This page is the thin
	// SvelteKit adapter: it holds reactive state and orchestrates the single BFF
	// client, while ordering, validation, status classification, and polling live
	// in the framework-agnostic $lib/onboarding modules (unit tested in Node).
	//
	// The final step reflects the TRUE saga outcome by POLLING GET /api/v1/orders/
	// {id} until a terminal state, never an optimistic "payment ok => activated".
	// Browser-only APIs (crypto, File, fetch) run only inside handlers / onMount,
	// so the adapter-node build (this group is ssr=false) does not break.
	import { onMount } from 'svelte';
	import {
		ApiError,
		api,
		type Addon,
		type OnboardingCatalog,
		type OnboardingOrderResult,
		type Tariff
	} from '$lib/api/client';
	import {
		computeMonthlyTotal,
		isRegistrationValid,
		nextStep,
		prevStep,
		type RegistrationForm,
		type WizardStep
	} from '$lib/onboarding/wizard';
	import { pollOrderStatus, type PollResult } from '$lib/onboarding/order-status';
	import {
		buildPaymentAttempt,
		recoveryActionFor,
		type RecoveryAction
	} from '$lib/onboarding/recovery';
	import { readFileAsBase64 } from '$lib/onboarding/file';
	import WizardProgress from './WizardProgress.svelte';
	import RegisterStep from './RegisterStep.svelte';
	import KycStep from './KycStep.svelte';
	import CatalogStep from './CatalogStep.svelte';
	import ReviewStep from './ReviewStep.svelte';
	import PaymentStep from './PaymentStep.svelte';
	import ResultStep from './ResultStep.svelte';

	const registration: RegistrationForm = $state({
		fullName: '',
		nationalId: '',
		email: '',
		phoneNumber: ''
	});

	let step = $state<WizardStep>('register');

	let kycFile = $state<File | null>(null);
	let kycFilename = $state('');

	let catalog = $state<OnboardingCatalog | null>(null);
	let catalogLoading = $state(false);
	let catalogError = $state('');
	let selectedTariffId = $state('');
	let selectedAddonIds = $state<string[]>([]);

	let order = $state<OnboardingOrderResult | null>(null);
	let placing = $state(false);
	let placeError = $state('');

	let paying = $state(false);
	let payError = $state('');

	let polling = $state(false);
	let pollLiveStatus = $state('');
	let pollAttempts = $state(0);
	let pollResult = $state<PollResult | null>(null);
	let pollError = $state('');

	const selectedTariff = $derived<Tariff | null>(
		catalog?.tariffs.find((tariff) => tariff.tariffId === selectedTariffId) ?? null
	);
	const selectedAddons = $derived<Addon[]>(
		catalog?.addons.filter((addon) => selectedAddonIds.includes(addon.addonId)) ?? []
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

	function onSelectTariff(tariffId: string) {
		selectedTariffId = tariffId;
	}

	function onToggleAddon(addonId: string) {
		selectedAddonIds = selectedAddonIds.includes(addonId)
			? selectedAddonIds.filter((id) => id !== addonId)
			: [...selectedAddonIds, addonId];
	}

	function onKycFile(file: File | null) {
		kycFile = file;
		kycFilename = file?.name ?? '';
	}

	const canAdvance = $derived.by(() => {
		switch (step) {
			case 'register':
				return isRegistrationValid(registration);
			case 'kyc':
				return kycFile !== null;
			case 'catalog':
				return selectedTariffId !== '' && !catalogLoading && catalog !== null;
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

	async function placeOrder() {
		if (placing) return;
		placing = true;
		placeError = '';
		try {
			const kycDocument = kycFile
				? {
						filename: kycFile.name,
						contentType: kycFile.type || 'application/octet-stream',
						content: await readFileAsBase64(kycFile)
					}
				: undefined;

			order = await api.placeOnboardingOrder(
				{
					tariffId: selectedTariffId,
					addonIds: selectedAddonIds,
					customer: {
						fullName: registration.fullName.trim(),
						email: registration.email.trim(),
						phoneNumber: registration.phoneNumber.trim(),
						nationalId: registration.nationalId.trim()
					},
					kycDocument
				},
				globalThis.crypto.randomUUID()
			);
			step = 'payment';
		} catch (error) {
			placeError = describeError(error, 'Could not place the order.');
		} finally {
			placing = false;
		}
	}

	async function pay() {
		if (paying || !order) return;
		paying = true;
		payError = '';
		try {
			// A fresh idempotency key per attempt: a retry after a failed charge must
			// NOT replay the previous key (payment-service would return the original
			// failure), so both the first charge and any retry go through here.
			await api.submitPayment(buildPaymentAttempt(order, monthlyTotal));
			// Payment accepted by the PSP - but activation is decided by the saga.
			// Advance to the result step and poll for the TRUE terminal outcome.
			step = 'result';
			await startPolling(order.orderId);
		} catch (error) {
			payError = describeError(error, 'Payment could not be processed. You can try again.');
		} finally {
			paying = false;
		}
	}

	// -- failure/compensation recovery (16.4.3) ------------------------------

	// The honest recovery path for a terminal failure, derived from the polled
	// saga status: a compensated/cancelled order offers a payment retry; a KYC
	// rejection routes back to re-verify identity. Non-failures offer nothing.
	const recoveryAction = $derived<RecoveryAction>(
		pollResult && pollResult.outcome === 'failed' ? recoveryActionFor(pollResult.status) : 'none'
	);

	function resetPaymentState() {
		paying = false;
		payError = '';
	}

	function resetPollState() {
		polling = false;
		pollLiveStatus = '';
		pollAttempts = 0;
		pollResult = null;
		pollError = '';
	}

	/**
	 * Retry payment on the SAME order after a payment failure/compensation. Routes
	 * back to the payment step; the next `pay()` builds a fresh idempotency key so
	 * the charge is attempted anew rather than replaying the failed one.
	 */
	function retryPayment() {
		resetPollState();
		payError = '';
		step = 'payment';
	}

	/**
	 * Corrective path for a KYC rejection: route back to the KYC step to re-upload
	 * a document. Registration details and the chosen plan are preserved; the
	 * rejected document and the failed order/poll are cleared so a fresh order is
	 * placed once a new document is provided.
	 */
	function restartKyc() {
		order = null;
		kycFile = null;
		kycFilename = '';
		resetPaymentState();
		resetPollState();
		step = 'kyc';
	}

	/**
	 * Full restart: keep the typed details and plan for convenience but drop the
	 * failed order/payment/poll so the wizard places a brand-new order from the top.
	 */
	function startOver() {
		order = null;
		resetPaymentState();
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
		} catch (error) {
			pollError = describeError(error, 'Could not confirm activation.');
		} finally {
			polling = false;
		}
	}

	function describeError(error: unknown, fallback: string): string {
		if (error instanceof ApiError) {
			return `${fallback} (HTTP ${error.status})`;
		}
		return fallback;
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
			<KycStep filename={kycFilename} onFileSelected={onKycFile} />
		{:else if step === 'catalog'}
			<CatalogStep
				{catalog}
				loading={catalogLoading}
				error={catalogError}
				{selectedTariffId}
				{selectedAddonIds}
				total={monthlyTotal}
				{currency}
				{onSelectTariff}
				{onToggleAddon}
			/>
		{:else if step === 'review'}
			<ReviewStep
				form={registration}
				filename={kycFilename}
				tariff={selectedTariff}
				addons={selectedAddons}
				total={monthlyTotal}
				{currency}
				{placing}
				error={placeError}
			/>
		{:else if step === 'payment'}
			<PaymentStep {order} total={monthlyTotal} {currency} submitting={paying} error={payError} />
		{:else if step === 'result'}
			<ResultStep
				{polling}
				liveStatus={pollLiveStatus}
				attempts={pollAttempts}
				result={pollResult}
				error={pollError}
				recovery={recoveryAction}
				onRetryPayment={retryPayment}
				onRestartKyc={restartKyc}
				onStartOver={startOver}
			/>
		{/if}
	</div>

	<div class="actions">
		{#if showBack}
			<button type="button" class="secondary" onclick={goBack} disabled={placing || paying}>
				Back
			</button>
		{/if}

		{#if step === 'register' || step === 'kyc' || step === 'catalog'}
			<button type="button" class="primary" onclick={goNext} disabled={!canAdvance}>
				Continue
			</button>
		{:else if step === 'review'}
			<button type="button" class="primary" onclick={placeOrder} disabled={placing}>
				Place order
			</button>
		{:else if step === 'payment'}
			<button type="button" class="primary" onclick={pay} disabled={paying}>Pay now</button>
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
