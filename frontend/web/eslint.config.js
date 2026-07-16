import js from '@eslint/js';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import ts from 'typescript-eslint';
import prettier from 'eslint-config-prettier';

// Flat config (ESLint 9) for the Telco CRM web frontend. Combines the JS and
// typescript-eslint recommended sets with eslint-plugin-svelte, and disables
// stylistic rules that Prettier owns. See package.json devDependencies.
export default ts.config(
	js.configs.recommended,
	...ts.configs.recommended,
	...svelte.configs['flat/recommended'],
	prettier,
	...svelte.configs['flat/prettier'],
	{
		languageOptions: {
			globals: {
				...globals.browser,
				...globals.node
			}
		},
		rules: {
			// Conventional underscore-prefixed args/vars are intentional placeholders.
			'@typescript-eslint/no-unused-vars': [
				'error',
				{ argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
			]
		}
	},
	{
		files: ['**/*.svelte'],
		languageOptions: {
			parserOptions: {
				parser: ts.parser
			}
		},
		rules: {
			// TypeScript (svelte-check) already resolves identifiers, including the
			// `generics="T"` type parameter on a component's <script>, which the core
			// no-undef rule cannot see and would falsely flag. Disable it for Svelte
			// files per the typescript-eslint recommendation.
			'no-undef': 'off'
		}
	},
	{
		ignores: ['build/', '.svelte-kit/', 'package/']
	}
);
