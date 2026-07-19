<script lang="ts">
	// Campaigns is a planned surface: the platform exposes no marketing/segmentation
	// endpoints, so rather than fake data this page states plainly what it will become.
	import ComingSoon from '$lib/ui/ComingSoon.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
</script>

<section class="page">
	<PageHeader title="Campaigns" subtitle="Plan and target marketing campaigns." />
	<ComingSoon
		title="Campaign management"
		message="Audience segmentation, campaign scheduling, and performance tracking are planned for the MARKETING_MANAGER role. They need campaign and segment services that the platform does not provide yet."
		cta="Register interest"
	/>
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}
</style>
