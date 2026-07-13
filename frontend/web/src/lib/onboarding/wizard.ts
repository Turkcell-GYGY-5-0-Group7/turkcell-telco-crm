// Framework-agnostic model for the onboarding wizard (subtask 16.4.2).
//
// The wizard walks a fixed, ordered sequence of steps; all step-navigation and
// per-step validation policy lives here as pure functions so it can be unit
// tested in Node without a DOM or a live backend. The SvelteKit page
// (`/onboarding/+page.svelte`) is a thin adapter that holds reactive state and
// calls into this module - it owns no ordering or validation rules of its own.
//
// The fields collected here are the fields the BFF actually needs: its
// `CustomerRegistration` record (type / firstName / lastName / identityNumber /
// dateOfBirth), which it relays verbatim to customer-service. There is NO payment
// step: charging is event-driven off `order.created.v1` (see $lib/api/client).

import type { Addon, CustomerType, KycDocumentType, Tariff } from '$lib/api/client';
import { isPastDate, isValidTckn } from './identity';
import { validateKycFile } from './file';

/**
 * Ordered wizard steps: register -> KYC upload -> tariff/addon selection ->
 * order review/placement -> polled activation result. `result` is terminal.
 *
 * There is deliberately no `payment` step. Placing the order is the only write:
 * order-service emits `order.created.v1` and payment-service's inbox consumer
 * charges from it (TELCO-CRM-MVP Section 9.2). The wizard observes the outcome by
 * polling the order, so a browser charge would double-charge (and would be a 403:
 * `POST /api/v1/payments` is ADMIN-only).
 */
export const WIZARD_STEPS = ['register', 'kyc', 'catalog', 'review', 'result'] as const;

export type WizardStep = (typeof WIZARD_STEPS)[number];

/** Human-readable step labels for the progress indicator. */
export const STEP_LABELS: Record<WizardStep, string> = {
	register: 'Register',
	kyc: 'Identity (KYC)',
	catalog: 'Choose plan',
	review: 'Review order',
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

/**
 * Registration form captured in the first step. One field per field of the BFF's
 * `CustomerRegistration` record - no more, no less. The MVP registers INDIVIDUAL
 * customers: `RegisterCustomerRequest.identityNumber` is annotated `@ValidTckn`
 * regardless of type, so a corporate VKN would be rejected downstream and the
 * wizard does not offer that path.
 */
export interface RegistrationForm {
	type: CustomerType;
	firstName: string;
	lastName: string;
	/** TCKN; checksum-validated here and again by customer-service. */
	identityNumber: string;
	/** ISO `YYYY-MM-DD` (Java `LocalDate`), must be in the past. */
	dateOfBirth: string;
}

/** KYC form captured in the second step (document type + the chosen file). */
export interface KycForm {
	type: KycDocumentType;
	file: File | null;
}

/**
 * Registration is valid when both names are present, the identity number is a
 * checksum-valid TCKN, and the date of birth is a past ISO date. These mirror
 * customer-service's own constraints (`@NotBlank`, `@ValidTckn`, `@Past`), so the
 * Next button no longer sends the user into a guaranteed 400. customer-service
 * remains the authority: its rejection is surfaced to the user verbatim.
 */
export function isRegistrationValid(form: RegistrationForm, today: Date = new Date()): boolean {
	return (
		form.firstName.trim().length > 0 &&
		form.lastName.trim().length > 0 &&
		isValidTckn(form.identityNumber) &&
		isPastDate(form.dateOfBirth.trim(), today)
	);
}

/**
 * The KYC step is satisfied once a document type is chosen and the chosen file passes the
 * single size/type policy in `./file.ts` ({@link validateKycFile}). The size check is part
 * of step validity - NOT an afterthought at submit time: an oversize document is only
 * rejected by customer-service AFTER the customer has already been registered, which would
 * leave the user half-onboarded with an unintelligible network error.
 */
export function isKycValid(form: KycForm): boolean {
	return (form.type === 'ID_CARD' || form.type === 'PASSPORT') && validateKycFile(form.file).valid;
}

/**
 * Monthly total for the selected tariff plus any addons, in the catalog's currency.
 * The BFF names the tariff price `monthlyPrice` and the addon price `price`; both
 * are read from the exact catalog fields here. Returns 0 when nothing is selected.
 */
export function computeMonthlyTotal(
	tariff: Pick<Tariff, 'monthlyPrice'> | null,
	addons: Pick<Addon, 'price'>[]
): number {
	const base = tariff?.monthlyPrice ?? 0;
	return addons.reduce((sum, addon) => sum + addon.price, base);
}
