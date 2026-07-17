// Pure geometry for the inline SVG Sparkline (unit tested in Node).
//
// No chart library is a dependency of this app (package.json has only
// oidc-client-ts); a usage trend is a single polyline, so its point maths lives
// here as a pure function and the .svelte wrapper only renders the string. Kept
// separate so the scaling - the part that can be wrong - is proven without a DOM.

export interface SparklineGeometry {
	/** `x,y` point pairs for an SVG <polyline points="...">. */
	points: string;
	/** The same points as tuples, for callers that draw their own marks. */
	coordinates: Array<[number, number]>;
}

/**
 * Map a series of values onto a `width` x `height` box. The first point sits at
 * x=0 and the last at x=width; y is inverted so larger values sit HIGHER (smaller
 * y), matching how a chart reads. A flat or single-point series is drawn along the
 * vertical middle rather than at an edge, so it reads as "steady", not "zero".
 * `padding` insets the curve from the top and bottom so peaks are not clipped by
 * the stroke width.
 */
export function toSparklineGeometry(
	values: readonly number[],
	width: number,
	height: number,
	padding = 1
): SparklineGeometry {
	const finite = values.filter((v) => Number.isFinite(v));
	if (finite.length === 0) {
		return { points: '', coordinates: [] };
	}

	const usableHeight = Math.max(0, height - padding * 2);
	const midline = height / 2;

	if (finite.length === 1) {
		const only: [number, number] = [width / 2, midline];
		return { points: `${only[0]},${only[1]}`, coordinates: [only] };
	}

	const min = Math.min(...finite);
	const max = Math.max(...finite);
	const span = max - min;
	const stepX = width / (finite.length - 1);

	const coordinates = finite.map((value, index): [number, number] => {
		const x = index * stepX;
		// A flat series (span 0) draws along the midline instead of pinned to a rail.
		const ratio = span === 0 ? 0.5 : (value - min) / span;
		const y = padding + (1 - ratio) * usableHeight;
		return [round(x), round(y)];
	});

	return {
		points: coordinates.map(([x, y]) => `${x},${y}`).join(' '),
		coordinates
	};
}

function round(value: number): number {
	return Math.round(value * 100) / 100;
}
