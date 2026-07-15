import { describe, expect, it } from 'vitest';
import { buildRegisterOrderRequest, buildReuseOrderRequest } from './order-request';
import type { RegistrationForm } from './wizard';
import type { KycDocumentPayload } from '$lib/api/client';

// These tests pin the onboarding order body to the BFF's ACTUAL Java records
// (OnboardingOrderRequest / CustomerRegistration / KycDocument). The wizard once sent a
// guessed shape (tariffId, addonIds, customer.fullName/email/phoneNumber) that the BFF
// could not accept; asserting the exact key sets here means that drift cannot return
// silently.

const registration: RegistrationForm = {
	type: 'INDIVIDUAL',
	firstName: '  Ada  ',
	lastName: '  Lovelace ',
	identityNumber: ' 10000000146 ',
	dateOfBirth: '1990-01-01'
};

const kycDocument: KycDocumentPayload = {
	type: 'ID_CARD',
	fileName: 'id.png',
	contentType: 'image/png',
	content: 'QUJD'
};

describe('buildRegisterOrderRequest', () => {
	const request = buildRegisterOrderRequest({
		registration,
		kycDocument,
		tariffCode: 'POSTPAID-M',
		addonCodes: ['DATA-5GB']
	});

	it('produces exactly the BFF OnboardingOrderRequest register-path keys', () => {
		expect(Object.keys(request).sort()).toEqual([
			'addonCodes',
			'customer',
			'kycDocument',
			'tariffCode'
		]);
		// The reuse-path field must be absent, or the BFF would skip register + KYC.
		expect(request.customerId).toBeUndefined();
	});

	it('carries the tariff and addon CODES (there is no tariffId/addonIds in this contract)', () => {
		expect(request.tariffCode).toBe('POSTPAID-M');
		expect(request.addonCodes).toEqual(['DATA-5GB']);
	});

	it('maps the customer onto CustomerRegistration and trims the values', () => {
		expect(request.customer).toEqual({
			type: 'INDIVIDUAL',
			firstName: 'Ada',
			lastName: 'Lovelace',
			identityNumber: '10000000146',
			dateOfBirth: '1990-01-01'
		});
	});

	it('passes the KYC document through with the BFF field names', () => {
		expect(request.kycDocument).toEqual({
			type: 'ID_CARD',
			fileName: 'id.png',
			contentType: 'image/png',
			content: 'QUJD'
		});
	});

	it('copies the addon codes so later selection changes cannot mutate a sent request', () => {
		const codes = ['DATA-5GB'];
		const built = buildRegisterOrderRequest({
			registration,
			kycDocument,
			tariffCode: 'POSTPAID-M',
			addonCodes: codes
		});
		codes.push('SMS-100');
		expect(built.addonCodes).toEqual(['DATA-5GB']);
	});
});

describe('buildReuseOrderRequest', () => {
	it('sends only the customerId and the plan - the BFF then skips register + KYC', () => {
		const request = buildReuseOrderRequest({
			customerId: 'c-1',
			tariffCode: 'POSTPAID-M',
			addonCodes: []
		});

		expect(request).toEqual({
			customerId: 'c-1',
			tariffCode: 'POSTPAID-M',
			addonCodes: []
		});
		expect(request.customer).toBeUndefined();
		expect(request.kycDocument).toBeUndefined();
	});
});
