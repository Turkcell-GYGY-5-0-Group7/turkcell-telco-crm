<script lang="ts">
	// Step indicator for the onboarding wizard (16.4.2). Purely presentational:
	// it reflects the current step from the framework-agnostic step model.
	//
	// Wide viewports get the full stepper - every step named, so the user can see how
	// much is left. Narrow ones get "Step n of 5" plus a bar: five labels squeezed
	// onto a phone are unreadable, and the count carries the same information.
	import { STEP_LABELS, WIZARD_STEPS, stepIndex, type WizardStep } from '$lib/onboarding/wizard';

	let { current }: { current: WizardStep } = $props();

	const currentIndex = $derived(stepIndex(current));
	const total = WIZARD_STEPS.length;
	const percent = $derived(((currentIndex + 1) / total) * 100);
</script>

<div class="progress">
	<ol class="steps" aria-label="Onboarding progress">
		{#each WIZARD_STEPS as step, index (step)}
			<li
				class="step"
				class:active={index === currentIndex}
				class:done={index < currentIndex}
				aria-current={index === currentIndex ? 'step' : undefined}
			>
				<span class="dot">
					{#if index < currentIndex}
						<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
					{:else}
						{index + 1}
					{/if}
				</span>
				<span class="label">{STEP_LABELS[step]}</span>
			</li>
		{/each}
	</ol>

	<div class="compact">
		<span class="compact-label">
			Step {currentIndex + 1} of {total} - {STEP_LABELS[WIZARD_STEPS[currentIndex]]}
		</span>
		<div class="bar" aria-hidden="true">
			<div class="bar-fill" style={`width: ${percent}%`}></div>
		</div>
	</div>
</div>

<style>
	.steps {
		display: flex;
		align-items: flex-start;
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.step {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-2);
		flex: 1;
		position: relative;
		text-align: center;
	}

	/* The connector is drawn as the step's own left edge, so it always spans exactly
	   the gap between two dots regardless of how the labels wrap. */
	.step:not(:first-child)::before {
		content: '';
		position: absolute;
		top: 0.94rem;
		right: 50%;
		left: -50%;
		height: 2px;
		background: var(--color-border-strong);
	}

	.step.done::before,
	.step.active::before {
		background: var(--tk-navy-700);
	}

	.dot {
		position: relative;
		z-index: 1;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2rem;
		height: 2rem;
		border-radius: var(--radius-full);
		background: var(--color-surface);
		border: 2px solid var(--color-border-strong);
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
		font-weight: 700;
	}

	.dot svg {
		width: 1rem;
		height: 1rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2.5;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	.step.done .dot {
		background: var(--tk-navy-700);
		border-color: var(--tk-navy-700);
		color: #ffffff;
	}

	.step.active .dot {
		background: var(--color-accent);
		border-color: var(--color-accent);
		color: var(--color-on-accent);
	}

	.label {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.step.active .label {
		color: var(--color-text);
		font-weight: 600;
	}

	.step.done .label {
		color: var(--color-text-secondary);
	}

	.compact {
		display: none;
		flex-direction: column;
		gap: var(--space-2);
	}

	.compact-label {
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text-secondary);
	}

	.bar {
		height: 0.375rem;
		border-radius: var(--radius-full);
		background: var(--color-border);
		overflow: hidden;
	}

	.bar-fill {
		height: 100%;
		border-radius: var(--radius-full);
		background: var(--color-accent);
		transition: width var(--duration-slow) var(--ease-out);
	}

	@media (max-width: 40rem) {
		.steps {
			display: none;
		}

		.compact {
			display: flex;
		}
	}
</style>
