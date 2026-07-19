import { describe, expect, it } from 'vitest';
import {
	WIZARD_STEPS,
	computeMonthlyTotal,
	isFirstStep,
	isKycValid,
	isLastStep,
	isRegistrationValid,
	nextStep,
	prevStep,
	stepIndex,
	type KycForm,
	type RegistrationForm
} from './wizard';

// Framework-agnostic proof of the wizard's ordering and per-step gating (16.4.2).
// The live click-through needs the running stack (deferred, Sprint 15 precedent).

describe('wizard step machine', () => {
	it('exposes the register -> ... -> result ordering, with NO payment step', () => {
		expect(WIZARD_STEPS).toEqual(['register', 'kyc', 'catalog', 'review', 'result']);
		// Charging is event-driven off order.created.v1 (TELCO-CRM-MVP 9.2); the browser
		// has no payment call to make (POST /api/v1/payments is ADMIN-only).
		expect(WIZARD_STEPS).not.toContain('payment');
	});

	it('advances through every step and clamps at the terminal result step', () => {
		expect(nextStep('register')).toBe('kyc');
		expect(nextStep('kyc')).toBe('catalog');
		expect(nextStep('catalog')).toBe('review');
		expect(nextStep('review')).toBe('result');
		expect(nextStep('result')).toBe('result');
	});

	it('walks back and clamps at the first step', () => {
		expect(prevStep('kyc')).toBe('register');
		expect(prevStep('register')).toBe('register');
	});

	it('reports first/last and index correctly', () => {
		expect(isFirstStep('register')).toBe(true);
		expect(isFirstStep('kyc')).toBe(false);
		expect(isLastStep('result')).toBe(true);
		expect(isLastStep('review')).toBe(false);
		expect(stepIndex('catalog')).toBe(2);
	});
});

// The registration form is the BFF's CustomerRegistration record: type / firstName /
// lastName / identityNumber / dateOfBirth. Its gating mirrors customer-service's own
// constraints (@NotBlank, @ValidTckn, @Past).
describe('isRegistrationValid', () => {
	const today = new Date('2026-07-13T00:00:00Z');
	const base: RegistrationForm = {
		type: 'INDIVIDUAL',
		firstName: 'Ada',
		lastName: 'Lovelace',
		identityNumber: '10000000146',
		dateOfBirth: '1990-01-01'
	};

	it('accepts a fully populated, well-formed form', () => {
		expect(isRegistrationValid(base, today)).toBe(true);
	});

	it('rejects a missing first or last name', () => {
		expect(isRegistrationValid({ ...base, firstName: '  ' }, today)).toBe(false);
		expect(isRegistrationValid({ ...base, lastName: '' }, today)).toBe(false);
	});

	it('rejects a checksum-invalid TCKN (customer-service @ValidTckn would 400 it)', () => {
		expect(isRegistrationValid({ ...base, identityNumber: '12345678901' }, today)).toBe(false);
		expect(isRegistrationValid({ ...base, identityNumber: '' }, today)).toBe(false);
	});

	it('rejects a missing or non-past date of birth (@Past)', () => {
		expect(isRegistrationValid({ ...base, dateOfBirth: '' }, today)).toBe(false);
		expect(isRegistrationValid({ ...base, dateOfBirth: '2030-01-01' }, today)).toBe(false);
	});
});

describe('isKycValid', () => {
	it('requires a document type and a file', () => {
		const file = new File(['x'], 'id.png', { type: 'image/png' });
		expect(isKycValid({ type: 'ID_CARD', file })).toBe(true);
		expect(isKycValid({ type: 'PASSPORT', file })).toBe(true);
		expect(isKycValid({ type: 'ID_CARD', file: null })).toBe(false);
		// Only customer-service's DocumentType values are accepted.
		expect(isKycValid({ type: 'DRIVING_LICENCE', file } as unknown as KycForm)).toBe(false);
	});

	it('does not let an oversize document leave the KYC step', () => {
		// A 6 MiB phone photo: accepted by the old wizard, then rejected by customer-service
		// AFTER the customer was already registered (HTTP 503 / "Failed to fetch" in the browser).
		const oversize = new File([new Uint8Array(6 * 1024 * 1024)], 'photo.jpg', {
			type: 'image/jpeg'
		});
		expect(isKycValid({ type: 'ID_CARD', file: oversize })).toBe(false);
	});

	it('does not let an unsupported document type leave the KYC step', () => {
		const clip = new File(['x'], 'clip.mp4', { type: 'video/mp4' });
		expect(isKycValid({ type: 'ID_CARD', file: clip })).toBe(false);
	});
});

describe('computeMonthlyTotal', () => {
	it('is 0 when no tariff is selected', () => {
		expect(computeMonthlyTotal(null, [])).toBe(0);
	});

	it('sums the tariff monthlyPrice and every selected addon price (BFF field names)', () => {
		expect(computeMonthlyTotal({ monthlyPrice: 100 }, [{ price: 20 }, { price: 5.5 }])).toBe(125.5);
	});
});
