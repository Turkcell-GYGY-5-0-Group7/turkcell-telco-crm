<script lang="ts">
	// An accessible tab strip. The caller supplies the tab list and draws the active
	// panel through the `panel` snippet, so this owns only selection and keyboard
	// navigation (arrow keys move between tabs, per the WAI-ARIA tabs pattern). The
	// active id is bindable so a parent can drive or read it.
	import type { Snippet } from 'svelte';

	interface Tab {
		id: string;
		label: string;
	}

	let {
		tabs,
		active = $bindable(),
		panel
	}: {
		tabs: Tab[];
		active?: string;
		/** Draws the panel for the active tab id. */
		panel: Snippet<[string]>;
	} = $props();

	// Default the selection to the first tab when the caller does not set one.
	$effect(() => {
		if (active === undefined && tabs.length > 0) {
			active = tabs[0].id;
		}
	});

	function onKeydown(event: KeyboardEvent, index: number) {
		let next = index;
		if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
		else if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
		else if (event.key === 'Home') next = 0;
		else if (event.key === 'End') next = tabs.length - 1;
		else return;
		event.preventDefault();
		active = tabs[next].id;
		const strip = event.currentTarget as HTMLElement;
		const buttons = strip.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]');
		buttons?.[next]?.focus();
	}
</script>

<div class="tabs">
	<div class="strip" role="tablist">
		{#each tabs as tab, index (tab.id)}
			<button
				type="button"
				role="tab"
				id={`tab-${tab.id}`}
				aria-selected={active === tab.id}
				aria-controls={`panel-${tab.id}`}
				tabindex={active === tab.id ? 0 : -1}
				class:active={active === tab.id}
				onclick={() => (active = tab.id)}
				onkeydown={(event) => onKeydown(event, index)}
			>
				{tab.label}
			</button>
		{/each}
	</div>

	{#if active !== undefined}
		<div id={`panel-${active}`} role="tabpanel" aria-labelledby={`tab-${active}`} class="panel">
			{@render panel(active)}
		</div>
	{/if}
</div>

<style>
	.tabs {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.strip {
		display: flex;
		gap: var(--space-1);
		border-bottom: 1px solid var(--color-border);
		overflow-x: auto;
	}

	.strip button {
		padding: var(--space-3) var(--space-4);
		border: 0;
		border-bottom: 2px solid transparent;
		background: none;
		font: inherit;
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text-secondary);
		white-space: nowrap;
		cursor: pointer;
		transition: color var(--duration-fast) var(--ease-out);
	}

	.strip button:hover {
		color: var(--color-text);
	}

	.strip button.active {
		color: var(--color-text);
		border-bottom-color: var(--color-accent);
	}
</style>
