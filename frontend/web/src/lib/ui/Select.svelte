<script lang="ts">
	// A labelled dropdown. Composes Field and binds `value`. Options are a plain
	// `{ value, label }[]` list so pages pass domain enums (ticket categories, teams)
	// without hand-writing <option> markup.
	import Field from './Field.svelte';

	interface Option {
		value: string;
		label: string;
	}

	let {
		id,
		label,
		value = $bindable(''),
		options,
		placeholder,
		hint,
		error,
		required = false,
		disabled = false,
		onchange
	}: {
		id: string;
		label: string;
		value?: string;
		options: Option[];
		/** Optional leading, unselectable prompt row. */
		placeholder?: string;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
		/** Fired after the bound value updates on a user selection. */
		onchange?: () => void;
	} = $props();
</script>

<Field {id} {label} {hint} {error} {required}>
	<div class="select-wrap">
		<select
			{id}
			{disabled}
			bind:value
			onchange={() => onchange?.()}
			aria-invalid={error ? 'true' : undefined}
			aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
		>
			{#if placeholder}
				<option value="" disabled>{placeholder}</option>
			{/if}
			{#each options as option (option.value)}
				<option value={option.value}>{option.label}</option>
			{/each}
		</select>
		<span class="chevron" aria-hidden="true">
			<svg viewBox="0 0 24 24"><path d="M6 9l6 6 6-6" /></svg>
		</span>
	</div>
</Field>

<style>
	.select-wrap {
		position: relative;
	}

	select {
		width: 100%;
		padding: 0.55rem 2.2rem 0.55rem 0.8rem;
		font: inherit;
		font-size: var(--text-sm-size);
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		appearance: none;
		cursor: pointer;
	}

	select:focus {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
		border-color: transparent;
	}

	select[aria-invalid='true'] {
		border-color: var(--color-danger-solid);
	}

	select:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}

	.chevron {
		position: absolute;
		top: 50%;
		right: 0.7rem;
		transform: translateY(-50%);
		pointer-events: none;
		color: var(--color-text-muted);
	}

	.chevron svg {
		width: 1.1rem;
		height: 1.1rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
		stroke-linejoin: round;
	}
</style>
