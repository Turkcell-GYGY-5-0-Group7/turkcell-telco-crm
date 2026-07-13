<script lang="ts">
	// Step 3: tariff + addon selection (16.4.2). Reads the UI-shaped catalog the
	// BFF composes (GET /bff/v1/onboarding/catalog); one tariff is required, addons
	// are optional. The running monthly total is computed by the wizard model.
	//
	// The catalog identifies both tariffs and addons by CODE (BFF `TariffOption.code`
	// / `AddonOption.code`) - there is no id in this contract - and the order request
	// carries those codes. Addons are filtered to the ones bound to the selected
	// tariff (`AddonOption.tariffCode`), which is how the BFF composes them.
	import type { OnboardingCatalog } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';

	let {
		catalog,
		loading,
		error,
		selectedTariffCode,
		selectedAddonCodes,
		total,
		currency,
		onSelectTariff,
		onToggleAddon
	}: {
		catalog: OnboardingCatalog | null;
		loading: boolean;
		error: string;
		selectedTariffCode: string;
		selectedAddonCodes: string[];
		total: number;
		currency: string;
		onSelectTariff: (tariffCode: string) => void;
		onToggleAddon: (addonCode: string) => void;
	} = $props();

	const availableAddons = $derived(
		catalog?.addons.filter((addon) => addon.tariffCode === selectedTariffCode) ?? []
	);
</script>

<div class="step-body">
	<h2>Choose your plan</h2>

	{#if loading}
		<p class="hint">Loading catalog...</p>
	{:else if error}
		<p class="error" role="alert">{error}</p>
	{:else if catalog}
		<fieldset>
			<legend>Tariff</legend>
			{#each catalog.tariffs as tariff (tariff.code)}
				<label class="option">
					<input
						type="radio"
						name="tariff"
						value={tariff.code}
						checked={selectedTariffCode === tariff.code}
						onchange={() => onSelectTariff(tariff.code)}
					/>
					<span class="option-name">{tariff.name}</span>
					<span class="option-price">{formatMoney(tariff.monthlyPrice, tariff.currency)}/mo</span>
				</label>
			{/each}
		</fieldset>

		{#if availableAddons.length > 0}
			<fieldset>
				<legend>Add-ons</legend>
				{#each availableAddons as addon (addon.code)}
					<label class="option">
						<input
							type="checkbox"
							value={addon.code}
							checked={selectedAddonCodes.includes(addon.code)}
							onchange={() => onToggleAddon(addon.code)}
						/>
						<span class="option-name">{addon.name}</span>
						<span class="option-price">{formatMoney(addon.price, addon.currency)}/mo</span>
					</label>
				{/each}
			</fieldset>
		{/if}

		<p class="total">Monthly total: <strong>{formatMoney(total, currency)}</strong></p>
	{/if}
</div>

<style>
	.step-body {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	h2 {
		margin: 0;
		font-size: 1.15rem;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.error {
		margin: 0;
		color: #b91c1c;
		font-size: 0.9rem;
	}

	fieldset {
		border: 1px solid #e5e7eb;
		border-radius: 0.5rem;
		padding: 0.75rem 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	legend {
		font-weight: 600;
		font-size: 0.9rem;
		color: #374151;
	}

	.option {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-size: 0.95rem;
	}

	.option-price {
		margin-left: auto;
		color: #4b5563;
	}

	.total {
		margin: 0;
		font-size: 1rem;
	}
</style>
