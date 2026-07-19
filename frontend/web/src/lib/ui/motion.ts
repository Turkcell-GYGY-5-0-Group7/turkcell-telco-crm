// Reduced-motion policy for JS-driven transitions.
//
// The global CSS guard in base.css neutralises CSS animations, but Svelte's
// fade/fly transitions are driven from JS and ignore it - they must be given a
// zero duration explicitly. Every call site passes its duration through
// motionDuration() so "reduce motion" genuinely means no motion.

/** True when the user has asked the OS for reduced motion. False during SSR. */
export function prefersReducedMotion(): boolean {
	if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
		return false;
	}
	return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** The requested duration, or 0 when the user has asked for reduced motion. */
export function motionDuration(ms: number): number {
	return prefersReducedMotion() ? 0 : ms;
}
