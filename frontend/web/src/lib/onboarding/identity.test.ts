import { describe, expect, it } from 'vitest';
import { isPastDate, isValidTckn } from './identity';

// The TCKN rule here is a port of customer-service's `TurkishNationalId.isValidTckn`
// (the server rule behind @ValidTckn). These cases pin the port to that algorithm: a
// naive "11 digits" input is NOT enough, which is exactly what a checksum-invalid
// number proves.

describe('isValidTckn', () => {
	it('accepts checksum-valid numbers', () => {
		// Checksums verified against the server algorithm:
		// d10 = ((d1+d3+d5+d7+d9)*7 - (d2+d4+d6+d8)) mod 10; d11 = (d1..d10) mod 10.
		for (const tckn of ['10000000146', '19191919190', '12345678950']) {
			expect(isValidTckn(tckn)).toBe(true);
		}
	});

	it('rejects an 11-digit number with a bad checksum (the naive-input case)', () => {
		expect(isValidTckn('12345678901')).toBe(false);
		expect(isValidTckn('11111111111')).toBe(false);
		expect(isValidTckn('10000000145')).toBe(false);
	});

	it('rejects a leading zero, wrong lengths, and non-digits', () => {
		expect(isValidTckn('01234567890')).toBe(false);
		expect(isValidTckn('1000000014')).toBe(false);
		expect(isValidTckn('100000001466')).toBe(false);
		expect(isValidTckn('1000000014a')).toBe(false);
		expect(isValidTckn('')).toBe(false);
	});

	it('tolerates surrounding whitespace', () => {
		expect(isValidTckn('  10000000146  ')).toBe(true);
	});
});

describe('isPastDate', () => {
	const today = new Date('2026-07-13T09:00:00Z');

	it('accepts an ISO date strictly in the past (customer-service @Past)', () => {
		expect(isPastDate('1990-01-01', today)).toBe(true);
	});

	it('rejects today and any future date', () => {
		expect(isPastDate('2026-07-13', today)).toBe(false);
		expect(isPastDate('2030-01-01', today)).toBe(false);
	});

	it('rejects an empty or non-ISO value', () => {
		expect(isPastDate('', today)).toBe(false);
		expect(isPastDate('01/01/1990', today)).toBe(false);
	});
});
