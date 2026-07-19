// Money formatting for the onboarding wizard (16.4.2). Pure and unit testable;
// falls back to a plain "<amount> <currency>" string if the runtime cannot
// resolve the currency code, so the UI never throws while rendering a price.

export function formatMoney(amount: number, currency: string): string {
	try {
		return new Intl.NumberFormat('en', { style: 'currency', currency }).format(amount);
	} catch {
		return `${amount.toFixed(2)} ${currency}`;
	}
}
