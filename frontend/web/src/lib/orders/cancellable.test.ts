import { describe, expect, it } from 'vitest';
import { isCancellable } from './cancellable';

describe('isCancellable', () => {
	it('allows PENDING and CONFIRMED', () => {
		expect(isCancellable('PENDING')).toBe(true);
		expect(isCancellable('CONFIRMED')).toBe(true);
		expect(isCancellable(' confirmed ')).toBe(true);
	});

	it('rejects terminal states', () => {
		expect(isCancellable('FULFILLED')).toBe(false);
		expect(isCancellable('CANCELLED')).toBe(false);
		expect(isCancellable('FAILED')).toBe(false);
	});

	it('rejects unknown/empty', () => {
		expect(isCancellable('')).toBe(false);
		expect(isCancellable('WHATEVER')).toBe(false);
	});
});
