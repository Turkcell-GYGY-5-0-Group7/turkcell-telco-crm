import { afterEach, describe, expect, it } from 'vitest';
import { clearAccessToken, getAccessToken, setAccessToken } from './token-store';

afterEach(() => {
	clearAccessToken();
});

describe('token-store', () => {
	it('returns null before any token is set', () => {
		expect(getAccessToken()).toBeNull();
	});

	it('returns the stored token after setAccessToken', () => {
		setAccessToken('access-123');
		expect(getAccessToken()).toBe('access-123');
	});

	it('overwrites the token on a subsequent set (silent renewal)', () => {
		setAccessToken('old-token');
		setAccessToken('rotated-token');
		expect(getAccessToken()).toBe('rotated-token');
	});

	it('treats an empty string as no token', () => {
		setAccessToken('');
		expect(getAccessToken()).toBeNull();
	});

	it('clears the token on setAccessToken(null) and clearAccessToken()', () => {
		setAccessToken('access-123');
		setAccessToken(null);
		expect(getAccessToken()).toBeNull();

		setAccessToken('access-456');
		clearAccessToken();
		expect(getAccessToken()).toBeNull();
	});
});
