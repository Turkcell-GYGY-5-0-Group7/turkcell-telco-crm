import { describe, expect, it } from 'vitest';
import { THEME_STORAGE_KEY, isTheme, resolveInitialTheme, toggleTheme } from './theme-core';

describe('isTheme', () => {
	it('accepts the two rendered themes', () => {
		expect(isTheme('light')).toBe(true);
		expect(isTheme('dark')).toBe(true);
	});

	it('rejects anything else', () => {
		expect(isTheme('system')).toBe(false);
		expect(isTheme('')).toBe(false);
		expect(isTheme(null)).toBe(false);
		expect(isTheme(undefined)).toBe(false);
		expect(isTheme(1)).toBe(false);
	});
});

describe('resolveInitialTheme', () => {
	it('honours an explicit stored choice over the default', () => {
		expect(resolveInitialTheme('light', true)).toBe('light');
		expect(resolveInitialTheme('dark', false)).toBe('dark');
	});

	it('defaults to dark when nothing is stored, regardless of OS preference', () => {
		expect(resolveInitialTheme(null, true)).toBe('dark');
		expect(resolveInitialTheme(null, false)).toBe('dark');
		expect(resolveInitialTheme(null)).toBe('dark');
	});

	it('ignores a corrupt stored value rather than trusting it (falls back to dark)', () => {
		expect(resolveInitialTheme('purple', true)).toBe('dark');
		expect(resolveInitialTheme('', false)).toBe('dark');
	});
});

describe('toggleTheme', () => {
	it('flips the theme', () => {
		expect(toggleTheme('light')).toBe('dark');
		expect(toggleTheme('dark')).toBe('light');
	});

	it('round-trips', () => {
		expect(toggleTheme(toggleTheme('light'))).toBe('light');
	});
});

describe('THEME_STORAGE_KEY', () => {
	it('is the key the pre-paint inline script in app.html reads', () => {
		expect(THEME_STORAGE_KEY).toBe('telco-theme');
	});
});
