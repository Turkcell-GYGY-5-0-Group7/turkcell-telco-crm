// Reactive theme state for the toggle.
//
// A thin browser adapter over the pure $lib/theme/theme-core rules. The initial
// theme is NOT resolved here: the blocking script in app.html already applied it
// to <html data-theme> before first paint (that is what prevents the flash), so
// init() simply adopts what is on the element and keeps it in sync from then on.

import { browser } from '$app/environment';
import { THEME_STORAGE_KEY, isTheme, toggleTheme, type Theme } from './theme-core';

class ThemeStore {
	current = $state<Theme>('light');

	/** Adopt the theme the pre-paint script already applied to <html>. */
	init() {
		if (!browser) {
			return;
		}
		const applied = document.documentElement.getAttribute('data-theme');
		this.current = isTheme(applied) ? applied : 'light';
	}

	/** Flip the theme, apply it to <html>, and persist it as an explicit choice. */
	toggle() {
		if (!browser) {
			return;
		}
		const next = toggleTheme(this.current);
		this.current = next;
		document.documentElement.setAttribute('data-theme', next);
		try {
			localStorage.setItem(THEME_STORAGE_KEY, next);
		} catch {
			// Private-browsing modes can refuse storage; the theme still applies for
			// this session, it just will not be remembered.
		}
	}
}

export const theme = new ThemeStore();
