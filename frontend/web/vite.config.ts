import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

// Port 3000 is the telco-web Keycloak redirect origin (ADR-022 / ADR-011).
// The dev and preview servers must bind here; strictPort fails loudly rather
// than silently drifting to another port.
export default defineConfig({
	plugins: [sveltekit()],
	server: {
		port: 3000,
		strictPort: true
	},
	preview: {
		port: 3000,
		strictPort: true
	},
	test: {
		// Unit tests for framework-agnostic modules (e.g. the BFF API client).
		environment: 'node',
		include: ['src/**/*.{test,spec}.{js,ts}']
	}
});
