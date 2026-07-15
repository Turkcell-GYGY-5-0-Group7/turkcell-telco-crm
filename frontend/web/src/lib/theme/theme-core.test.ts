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
	it('honours an explicit stored choice over the system preference', () => {
		expect(resolveInitialTheme('light', true)).toBe('light');
		expect(resolveInitialTheme('dark', false)).toBe('dark');
	});

	it('falls back to the system preference when nothing is stored', () => {
		expect(resolveInitialTheme(null, true)).toBe('dark');
		expect(resolveInitialTheme(null, false)).toBe('light');
	});

	it('ignores a corrupt stored value rather than trusting it', () => {
		expect(resolveInitialTheme('purple', true)).toBe('dark');
		expect(resolveInitialTheme('', false)).toBe('light');
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
