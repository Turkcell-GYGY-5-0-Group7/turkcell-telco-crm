<script lang="ts">
	// Step 3: tariff + addon selection (16.4.2). Reads the UI-shaped catalog the
	// BFF composes (GET /bff/v1/onboarding/catalog); one tariff is required, addons
	// are optional. The running monthly total is computed by the wizard model.
	import type { OnboardingCatalog } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';

	let {
		catalog,
		loading,
		error,
		selectedTariffId,
		selectedAddonIds,
		total,
		currency,
		onSelectTariff,
		onToggleAddon
	}: {
		catalog: OnboardingCatalog | null;
		loading: boolean;
		error: string;
		selectedTariffId: string;
		selectedAddonIds: string[];
		total: number;
		currency: string;
		onSelectTariff: (tariffId: string) => void;
		onToggleAddon: (addonId: string) => void;
	} = $props();
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
			{#each catalog.tariffs as tariff (tariff.tariffId)}
				<label class="option">
					<input
						type="radio"
						name="tariff"
						value={tariff.tariffId}
						checked={selectedTariffId === tariff.tariffId}
						onchange={() => onSelectTariff(tariff.tariffId)}
					/>
					<span class="option-name">{tariff.name}</span>
					<span class="option-price">{formatMoney(tariff.monthlyPrice, tariff.currency)}/mo</span>
				</label>
			{/each}
		</fieldset>

		{#if catalog.addons.length > 0}
			<fieldset>
				<legend>Add-ons</legend>
				{#each catalog.addons as addon (addon.addonId)}
					<label class="option">
						<input
							type="checkbox"
							value={addon.addonId}
							checked={selectedAddonIds.includes(addon.addonId)}
							onchange={() => onToggleAddon(addon.addonId)}
						/>
						<span class="option-name">{addon.name}</span>
						<span class="option-price">{formatMoney(addon.monthlyPrice, addon.currency)}/mo</span>
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
