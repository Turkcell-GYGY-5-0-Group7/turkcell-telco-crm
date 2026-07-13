// Client-side TCKN (Turkish national identity number) checksum validation.
//
// customer-service validates `identityNumber` with `@ValidTckn`, which delegates to
// the domain rule in `TurkishNationalId.isValidTckn`: 11 digits, a non-zero leading
// digit, and two trailing CHECKSUM digits. A naive "11 digits" input is therefore
// rejected with a 400 by the server, and the BFF surfaces that as a validation
// error. This module is a faithful port of that server rule so the wizard can gate
// the field BEFORE the round-trip; it is a convenience only - customer-service
// remains the authority and its rejection is still surfaced verbatim by the wizard.
//
// Server source: microservices/customer-service/src/main/java/com/telco/customer/
// domain/validation/TurkishNationalId.java

const ELEVEN_DIGITS = /^\d{11}$/;

/**
 * True when `value` is a checksum-valid TCKN, matching the server rule exactly:
 * - 11 digits, first digit non-zero;
 * - digit 10 = ((d1+d3+d5+d7+d9) * 7 - (d2+d4+d6+d8)) mod 10;
 * - digit 11 = (d1+...+d10) mod 10.
 */
export function isValidTckn(value: string): boolean {
	const candidate = value.trim();
	if (!ELEVEN_DIGITS.test(candidate) || candidate.startsWith('0')) {
		return false;
	}

	const d = [...candidate].map((digit) => Number(digit));

	const oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
	const evenSum = d[1] + d[3] + d[5] + d[7];
	// Java's Math.floorMod: a non-negative remainder even when the operand is negative.
	const check10 = (((oddSum * 7 - evenSum) % 10) + 10) % 10;
	if (check10 !== d[9]) {
		return false;
	}

	const firstTenSum = d.slice(0, 10).reduce((sum, digit) => sum + digit, 0);
	return firstTenSum % 10 === d[10];
}

/**
 * True when `value` is a date-of-birth customer-service accepts: an ISO `YYYY-MM-DD`
 * date strictly in the past (`@Past` on `RegisterCustomerRequest.dateOfBirth`).
 * `today` is injectable so the rule is testable without a frozen clock.
 */
export function isPastDate(value: string, today: Date = new Date()): boolean {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
		return false;
	}
	const parsed = Date.parse(`${value}T00:00:00Z`);
	if (Number.isNaN(parsed)) {
		return false;
	}
	const startOfToday = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
	return parsed < startOfToday;
}
