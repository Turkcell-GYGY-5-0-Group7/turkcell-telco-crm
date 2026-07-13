<script lang="ts">
	// Step indicator for the onboarding wizard (16.4.2). Purely presentational:
	// it reflects the current step from the framework-agnostic step model.
	import { STEP_LABELS, WIZARD_STEPS, stepIndex, type WizardStep } from '$lib/onboarding/wizard';

	let { current }: { current: WizardStep } = $props();

	const currentIndex = $derived(stepIndex(current));
</script>

<ol class="progress" aria-label="Onboarding progress">
	{#each WIZARD_STEPS as step, index (step)}
		<li
			class="step"
			class:active={index === currentIndex}
			class:done={index < currentIndex}
			aria-current={index === currentIndex ? 'step' : undefined}
		>
			<span class="dot">{index + 1}</span>
			<span class="label">{STEP_LABELS[step]}</span>
		</li>
	{/each}
</ol>

<style>
	.progress {
		display: flex;
		flex-wrap: wrap;
		gap: 0.75rem;
		list-style: none;
		margin: 0 0 1.5rem;
		padding: 0;
	}

	.step {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		color: #9ca3af;
		font-size: 0.9rem;
	}

	.step.active {
		color: #16213e;
		font-weight: 600;
	}

	.step.done {
		color: #16a34a;
	}

	.dot {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1.5rem;
		height: 1.5rem;
		border-radius: 50%;
		border: 1px solid currentColor;
		font-size: 0.8rem;
	}
</style>
