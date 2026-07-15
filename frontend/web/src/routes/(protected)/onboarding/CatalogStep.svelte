<script lang="ts">
	// Step 3: tariff + addon selection (16.4.2). Reads the UI-shaped catalog the
	// BFF composes (GET /bff/v1/onboarding/catalog); one tariff is required, addons
	// are optional. The running monthly total is computed by the wizard model.
	//
	// The catalog identifies both tariffs and addons by CODE (BFF `TariffOption.code`
	// / `AddonOption.code`) - there is no id in this contract - and the order request
	// carries those codes. Addons are filtered to the ones bound to the selected
	// tariff (`AddonOption.tariffCode`), which is how the BFF composes them.
	//
	// Each option is a card wrapping its native radio/checkbox: the input is still the
	// control (keyboard, screen reader, form semantics all come free), the card is
	// only its hit area.
	import type { OnboardingCatalog } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';
	import Alert from '$lib/ui/Alert.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';

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
	<header>
		<h2>Choose your plan</h2>
		<p class="hint">Pick a tariff, then any add-ons that come with it.</p>
	</header>

	{#if loading}
		<div class="options" aria-busy="true" aria-label="Loading catalog">
			{#each [0, 1, 2] as index (index)}
				<div class="option-skeleton">
					<Skeleton variant="text" width="40%" />
					<Skeleton variant="text" width="70%" />
				</div>
			{/each}
		</div>
	{:else if error}
		<Alert tone="danger">
			<p>{error}</p>
		</Alert>
	{:else if catalog}
		<fieldset>
			<legend>Tariff</legend>
			<div class="options">
				{#each catalog.tariffs as tariff (tariff.code)}
					<label class="option" class:selected={selectedTariffCode === tariff.code}>
						<input
							type="radio"
							name="tariff"
							value={tariff.code}
							checked={selectedTariffCode === tariff.code}
							onchange={() => onSelectTariff(tariff.code)}
						/>
						<span class="option-text">
							<span class="option-name">{tariff.name}</span>
							{#if tariff.description}
								<span class="option-desc">{tariff.description}</span>
							{/if}
						</span>
						<span class="option-price tabular">
							{formatMoney(tariff.monthlyPrice, tariff.currency)}<span class="per">/mo</span>
						</span>
					</label>
				{/each}
			</div>
		</fieldset>

		{#if availableAddons.length > 0}
			<fieldset>
				<legend>Add-ons</legend>
				<div class="options">
					{#each availableAddons as addon (addon.code)}
						<label class="option" class:selected={selectedAddonCodes.includes(addon.code)}>
							<input
								type="checkbox"
								value={addon.code}
								checked={selectedAddonCodes.includes(addon.code)}
								onchange={() => onToggleAddon(addon.code)}
							/>
							<span class="option-text">
								<span class="option-name">{addon.name}</span>
							</span>
							<span class="option-price tabular">
								{formatMoney(addon.price, addon.currency)}<span class="per">/mo</span>
							</span>
						</label>
					{/each}
				</div>
			</fieldset>
		{/if}

		<p class="total">
			<span class="total-label">Monthly total</span>
			<strong class="total-value tabular">{formatMoney(total, currency)}</strong>
		</p>
	{/if}
</div>

<style>
	.step-body {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	header {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	h2 {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	fieldset {
		margin: 0;
		padding: 0;
		border: 0;
	}

	legend {
		padding: 0 0 var(--space-3);
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text-secondary);
	}

	.options {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.option {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4);
		border: 2px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface);
		cursor: pointer;
		transition:
			border-color var(--duration-fast) var(--ease-out),
			background-color var(--duration-fast) var(--ease-out);
	}

	.option:hover {
		border-color: var(--color-border-strong);
	}

	.option.selected {
		border-color: var(--tk-navy-600);
		background: var(--color-surface-alt);
	}

	.option:focus-within {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	.option input {
		width: 1.1rem;
		height: 1.1rem;
		flex-shrink: 0;
		accent-color: var(--tk-navy-600);
	}

	.option-text {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		min-width: 0;
	}

	.option-name {
		font-size: var(--text-sm-size);
		font-weight: 600;
	}

	.option-desc {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.option-price {
		margin-left: auto;
		font-weight: 700;
		white-space: nowrap;
	}

	.per {
		font-weight: 400;
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.option-skeleton {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-4);
		border: 2px solid var(--color-border);
		border-radius: var(--radius-lg);
	}

	.total {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-4) var(--space-5);
		border-radius: var(--radius-md);
		background: var(--color-accent-soft);
		color: var(--color-on-accent-soft);
	}

	.total-label {
		font-size: var(--text-sm-size);
		font-weight: 600;
	}

	.total-value {
		font-size: var(--text-xl-size);
		line-height: var(--text-xl-lh);
		font-weight: 700;
	}
</style>
