// Framework-agnostic model for the onboarding wizard (subtask 16.4.2).
//
// The wizard walks a fixed, ordered sequence of steps; all step-navigation and
// per-step validation policy lives here as pure functions so it can be unit
// tested in Node without a DOM or a live backend. The SvelteKit page
// (`/onboarding/+page.svelte`) is a thin adapter that holds reactive state and
// calls into this module - it owns no ordering or validation rules of its own.

import type { Addon, Tariff } from '$lib/api/client';

/**
 * Ordered wizard steps: register -> KYC upload -> tariff/addon selection ->
 * order review/placement -> payment -> polled result. `result` is terminal.
 */
export const WIZARD_STEPS = ['register', 'kyc', 'catalog', 'review', 'payment', 'result'] as const;

export type WizardStep = (typeof WIZARD_STEPS)[number];

/** Human-readable step labels for the progress indicator. */
export const STEP_LABELS: Record<WizardStep, string> = {
	register: 'Register',
	kyc: 'Identity (KYC)',
	catalog: 'Choose plan',
	review: 'Review order',
	payment: 'Payment',
	result: 'Activation'
};

/** Zero-based index of a step in {@link WIZARD_STEPS}. */
export function stepIndex(step: WizardStep): number {
	return WIZARD_STEPS.indexOf(step);
}

export function isFirstStep(step: WizardStep): boolean {
	return stepIndex(step) === 0;
}

export function isLastStep(step: WizardStep): boolean {
	return stepIndex(step) === WIZARD_STEPS.length - 1;
}

/** The next step, or the same step when already at the end. */
export function nextStep(step: WizardStep): WizardStep {
	const index = stepIndex(step);
	return WIZARD_STEPS[Math.min(index + 1, WIZARD_STEPS.length - 1)];
}

/** The previous step, or the same step when already at the start. */
export function prevStep(step: WizardStep): WizardStep {
	const index = stepIndex(step);
	return WIZARD_STEPS[Math.max(index - 1, 0)];
}

/** Registration form captured in the first step. */
export interface RegistrationForm {
	fullName: string;
	nationalId: string;
	email: string;
	phoneNumber: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Registration is valid when a name and a well-formed email and a phone number
 * and a national id (TCKN/VKN) are present. Kept deliberately lightweight - the
 * authoritative validation is customer-service's; this only gates the Next
 * button so the user is not sent forward with an obviously empty form.
 */
export function isRegistrationValid(form: RegistrationForm): boolean {
	return (
		form.fullName.trim().length > 0 &&
		EMAIL_RE.test(form.email.trim()) &&
		form.phoneNumber.trim().length > 0 &&
		form.nationalId.trim().length > 0
	);
}

/**
 * Monthly total for the selected tariff plus any addons, in the tariff's minor
 * unit as delivered by the catalog. Returns 0 when nothing is selected.
 */
export function computeMonthlyTotal(
	tariff: Pick<Tariff, 'monthlyPrice'> | null,
	addons: Pick<Addon, 'monthlyPrice'>[]
): number {
	const base = tariff?.monthlyPrice ?? 0;
	return addons.reduce((sum, addon) => sum + addon.monthlyPrice, base);
}
