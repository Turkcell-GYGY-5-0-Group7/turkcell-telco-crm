<script lang="ts">
	// A labelled text input. Composes Field for the label/hint/error chrome and
	// binds `value`, so a page writes `<Input label=... bind:value=... />` and gets
	// consistent styling and accessibility. Non-text types (date, number, email) are
	// supported via `type`.
	import Field from './Field.svelte';

	let {
		id,
		label,
		value = $bindable(''),
		type = 'text',
		placeholder,
		hint,
		error,
		required = false,
		disabled = false
	}: {
		id: string;
		label: string;
		value?: string;
		type?: 'text' | 'email' | 'date' | 'number' | 'search' | 'tel';
		placeholder?: string;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
	} = $props();
</script>

<Field {id} {label} {hint} {error} {required}>
	<input
		{id}
		{type}
		{placeholder}
		{disabled}
		bind:value
		aria-invalid={error ? 'true' : undefined}
		aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
	/>
</Field>

<style>
	input {
		width: 100%;
		padding: 0.55rem 0.8rem;
		font: inherit;
		font-size: var(--text-sm-size);
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		transition: border-color var(--duration-fast) var(--ease-out);
	}

	input::placeholder {
		color: var(--color-text-muted);
	}

	input:focus {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
		border-color: transparent;
	}

	input[aria-invalid='true'] {
		border-color: var(--color-danger-solid);
	}

	input:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}
</style>
