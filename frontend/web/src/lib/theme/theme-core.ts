// Theme resolution for the light/dark switch.
//
// Pure and framework-agnostic (unit tested in Node), because the same three rules
// are applied from two very different places: the blocking inline script in
// app.html (which must decide the theme BEFORE first paint, to avoid a flash of
// the wrong theme) and the reactive store the toggle drives. Keeping the rules
// here means those two can never disagree.
//
// There is no tri-state "system" mode: an untouched install follows the OS
// preference, and the first explicit toggle becomes a persisted choice.

/** The two themes the app renders. Mirrors the `data-theme` attribute on <html>. */
export type Theme = 'light' | 'dark';

/** localStorage key holding the user's explicit choice, if they have made one. */
export const THEME_STORAGE_KEY = 'telco-theme';

/** Narrow an unknown (a localStorage read, a DOM attribute) to a Theme. */
export function isTheme(value: unknown): value is Theme {
	return value === 'light' || value === 'dark';
}

/**
 * The theme to render on load: the user's stored choice when they have made one,
 * otherwise the OS preference. A missing or corrupt stored value is ignored rather
 * than trusted, so a bad localStorage entry can never render an unreadable page.
 */
export function resolveInitialTheme(stored: string | null, systemPrefersDark: boolean): Theme {
	if (isTheme(stored)) {
		return stored;
	}
	return systemPrefersDark ? 'dark' : 'light';
}

/** The other theme. */
export function toggleTheme(current: Theme): Theme {
	return current === 'dark' ? 'light' : 'dark';
}
