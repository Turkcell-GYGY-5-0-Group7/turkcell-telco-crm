<script lang="ts">
	// Persistent, in-context message: a failed load with its Retry, a rejected order,
	// a saga that is still running. Distinct from a toast on purpose - a toast is a
	// transient acknowledgement, whereas these messages ARE the state of the page and
	// must not disappear on a timer.
	//
	// `role` is the caller's call: 'alert' interrupts the screen reader (a genuine
	// failure), 'status' is announced politely (progress, a timeout notice).
	import type { Snippet } from 'svelte';

	let {
		tone,
		role = 'alert',
		children,
		actions
	}: {
		tone: 'danger' | 'warning' | 'info' | 'success';
		role?: 'alert' | 'status';
		children: Snippet;
		/** Optional trailing controls, e.g. a Retry button. */
		actions?: Snippet;
	} = $props();
</script>

<div class={`alert ${tone}`} {role}>
	<span class="bar" aria-hidden="true"></span>
	<div class="body">
		{@render children()}
	</div>
	{#if actions}
		<div class="actions">{@render actions()}</div>
	{/if}
</div>

<style>
	.alert {
		display: flex;
		align-items: center;
		gap: var(--space-4);
		position: relative;
		padding: var(--space-4) var(--space-5);
		padding-left: var(--space-5);
		border-radius: var(--radius-md);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
		overflow: hidden;
	}

	.bar {
		position: absolute;
		inset: 0 auto 0 0;
		width: 4px;
	}

	.body {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.actions {
		display: flex;
		gap: var(--space-2);
		flex-shrink: 0;
	}

	.danger {
		background: var(--color-danger-bg);
		color: var(--color-danger-text);
	}

	.danger .bar {
		background: var(--color-danger-solid);
	}

	.warning {
		background: var(--color-warning-bg);
		color: var(--color-warning-text);
	}

	.warning .bar {
		background: var(--color-warning-solid);
	}

	.info {
		background: var(--color-info-bg);
		color: var(--color-info-text);
	}

	.info .bar {
		background: var(--color-info-solid);
	}

	.success {
		background: var(--color-success-bg);
		color: var(--color-success-text);
	}

	.success .bar {
		background: var(--color-success-solid);
	}

	@media (max-width: 40rem) {
		.alert {
			flex-direction: column;
			align-items: stretch;
		}
	}
</style>
