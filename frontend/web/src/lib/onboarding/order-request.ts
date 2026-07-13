// The single place that builds the `POST /bff/v1/onboarding/order` request body
// (BFF `OnboardingOrderRequest`). Pure and unit tested, so the wire shape is
// pinned against the server contract instead of being re-guessed in a component.
//
// The BFF accepts exactly two identity paths (OnboardingCompositionService
// .resolveCustomer):
//   REGISTER - `customer` + `kycDocument` present, no `customerId`: the BFF
//              registers the customer, uploads the KYC document, then orders.
//   REUSE    - `customerId` present: the BFF skips register + KYC entirely.
// Sending a `customerId` AND registration details is not a supported combination
// (the BFF would silently ignore the registration), so the builder emits one or the
// other, never both. The reuse path is what lets the wizard re-place a failed order
// without a duplicate registration (customer-service would reject a second TCKN).

import type {
	CustomerRegistration,
	KycDocumentPayload,
	OnboardingOrderRequest
} from '$lib/api/client';
import type { RegistrationForm } from './wizard';

/** Trim-and-copy the registration form into the BFF's `CustomerRegistration` shape. */
export function toCustomerRegistration(form: RegistrationForm): CustomerRegistration {
	return {
		type: form.type,
		firstName: form.firstName.trim(),
		lastName: form.lastName.trim(),
		identityNumber: form.identityNumber.trim(),
		dateOfBirth: form.dateOfBirth.trim()
	};
}

/**
 * Build the REGISTER-path body: a new customer plus their KYC document, the selected
 * tariff CODE, and the selected addon CODES (the BFF resolves the code to a tariff id
 * server-side; there is no tariff id in this contract).
 */
export function buildRegisterOrderRequest(input: {
	registration: RegistrationForm;
	kycDocument: KycDocumentPayload;
	tariffCode: string;
	addonCodes: string[];
}): OnboardingOrderRequest {
	return {
		customer: toCustomerRegistration(input.registration),
		kycDocument: input.kycDocument,
		tariffCode: input.tariffCode,
		addonCodes: [...input.addonCodes]
	};
}

/**
 * Build the REUSE-path body for an already-registered customer: no registration and no
 * document, only the customer id and the plan. Used when re-placing an order after a
 * failed saga, where the customer (and their KYC document) already exist.
 */
export function buildReuseOrderRequest(input: {
	customerId: string;
	tariffCode: string;
	addonCodes: string[];
}): OnboardingOrderRequest {
	return {
		customerId: input.customerId,
		tariffCode: input.tariffCode,
		addonCodes: [...input.addonCodes]
	};
}
