<script lang="ts">
	// A labelled multi-line input (ticket bodies, comments). Composes Field and binds
	// `value`, matching Input/Select so a form reads consistently.
	import Field from './Field.svelte';

	let {
		id,
		label,
		value = $bindable(''),
		rows = 4,
		placeholder,
		hint,
		error,
		required = false,
		disabled = false
	}: {
		id: string;
		label: string;
		value?: string;
		rows?: number;
		placeholder?: string;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
	} = $props();
</script>

<Field {id} {label} {hint} {error} {required}>
	<textarea
		{id}
		{rows}
		{placeholder}
		{disabled}
		bind:value
		aria-invalid={error ? 'true' : undefined}
		aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
	></textarea>
</Field>

<style>
	textarea {
		width: 100%;
		padding: 0.55rem 0.8rem;
		font: inherit;
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		resize: vertical;
	}

	textarea::placeholder {
		color: var(--color-text-muted);
	}

	textarea:focus {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
		border-color: transparent;
	}

	textarea[aria-invalid='true'] {
		border-color: var(--color-danger-solid);
	}

	textarea:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}
</style>
