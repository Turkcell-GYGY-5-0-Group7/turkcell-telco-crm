import { describe, expect, it } from 'vitest';
import {
	WIZARD_STEPS,
	computeMonthlyTotal,
	isFirstStep,
	isLastStep,
	isRegistrationValid,
	nextStep,
	prevStep,
	stepIndex,
	type RegistrationForm
} from './wizard';

// Framework-agnostic proof of the wizard's ordering and per-step gating (16.4.2).
// The live click-through needs the running stack (deferred, Sprint 15 precedent).

describe('wizard step machine', () => {
	it('exposes the register -> ... -> result ordering', () => {
		expect(WIZARD_STEPS).toEqual(['register', 'kyc', 'catalog', 'review', 'payment', 'result']);
	});

	it('advances through every step and clamps at the terminal result step', () => {
		expect(nextStep('register')).toBe('kyc');
		expect(nextStep('kyc')).toBe('catalog');
		expect(nextStep('catalog')).toBe('review');
		expect(nextStep('review')).toBe('payment');
		expect(nextStep('payment')).toBe('result');
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
		expect(isLastStep('payment')).toBe(false);
		expect(stepIndex('catalog')).toBe(2);
	});
});

describe('isRegistrationValid', () => {
	const base: RegistrationForm = {
		fullName: 'Ada Lovelace',
		nationalId: '12345678901',
		email: 'ada@example.com',
		phoneNumber: '+905550001122'
	};

	it('accepts a fully populated, well-formed form', () => {
		expect(isRegistrationValid(base)).toBe(true);
	});

	it('rejects a missing name, phone, or national id', () => {
		expect(isRegistrationValid({ ...base, fullName: '  ' })).toBe(false);
		expect(isRegistrationValid({ ...base, phoneNumber: '' })).toBe(false);
		expect(isRegistrationValid({ ...base, nationalId: '' })).toBe(false);
	});

	it('rejects a malformed email', () => {
		expect(isRegistrationValid({ ...base, email: 'not-an-email' })).toBe(false);
		expect(isRegistrationValid({ ...base, email: 'a@b' })).toBe(false);
	});
});

describe('computeMonthlyTotal', () => {
	it('is 0 when no tariff is selected', () => {
		expect(computeMonthlyTotal(null, [])).toBe(0);
	});

	it('sums the tariff and every selected addon', () => {
		expect(
			computeMonthlyTotal({ monthlyPrice: 100 }, [{ monthlyPrice: 20 }, { monthlyPrice: 5.5 }])
		).toBe(125.5);
	});
});
