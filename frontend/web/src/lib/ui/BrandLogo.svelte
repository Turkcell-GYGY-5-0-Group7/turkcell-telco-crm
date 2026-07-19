<script lang="ts">
	// The app's brand mark: an original signal-arc glyph on the accent fill, plus a
	// wordmark ("Telco CRM"). It is an original mark for this demo platform, not any
	// real company's logo. The mark is inline SVG rather than an asset so it inherits
	// the theme and needs no request.
	let {
		variant = 'header'
	}: {
		/** header = compact, on the navy bar; hero = large, on a page surface. */
		variant?: 'header' | 'hero';
	} = $props();
</script>

<span class={`brand ${variant}`}>
	<svg class="mark" viewBox="0 0 32 32" aria-hidden="true">
		<rect width="32" height="32" rx="9" fill="var(--color-accent)" />
		<g fill="none" stroke="var(--color-on-accent)" stroke-width="2.6" stroke-linecap="round">
			<path d="M11 21a7 7 0 0 1 7-7" />
			<path d="M11 21a11.5 11.5 0 0 1 11.5-11.5" />
		</g>
		<circle cx="11" cy="21" r="2.4" fill="var(--color-on-accent)" />
	</svg>
	<span class="wordmark">
		<span class="name">Telco</span>
		<span class="suffix">CRM</span>
	</span>
</span>

<style>
	.brand {
		display: inline-flex;
		align-items: center;
		gap: var(--space-3);
	}

	.mark {
		flex-shrink: 0;
		width: 1.75rem;
		height: 1.75rem;
	}

	.hero .mark {
		width: 3rem;
		height: 3rem;
	}

	.wordmark {
		display: inline-flex;
		align-items: baseline;
		gap: 0.35rem;
		font-family: var(--font-display);
		font-weight: 700;
		letter-spacing: -0.01em;
	}

	.name {
		font-size: var(--text-lg-size);
		color: var(--color-header-text);
	}

	/* Hero wordmark echoes the Keycloak title: uppercase, wide-tracked, lighter. */
	.hero .name {
		font-size: var(--text-2xl-size);
		font-weight: 500;
		text-transform: uppercase;
		letter-spacing: 0.14em;
		color: var(--color-text);
	}

	.suffix {
		font-size: var(--text-xs-size);
		letter-spacing: 0.08em;
		color: var(--color-accent);
	}

	.hero .suffix {
		font-size: var(--text-sm-size);
		text-transform: uppercase;
		letter-spacing: 0.14em;
		color: var(--color-text-secondary);
	}
</style>
