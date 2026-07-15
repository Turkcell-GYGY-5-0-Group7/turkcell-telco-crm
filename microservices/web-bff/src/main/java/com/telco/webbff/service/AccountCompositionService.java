package com.telco.webbff.service;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.webbff.client.GatewayClient;
import com.telco.webbff.dto.AccountResponse;
import com.telco.webbff.dto.AccountSubscription;
import com.telco.webbff.dto.HomeResponse;
import com.telco.webbff.dto.InvoiceSummary;
import com.telco.webbff.dto.InvoicesResponse;
import com.telco.webbff.dto.ProfileSummary;
import com.telco.webbff.dto.SubscriptionSummary;
import com.telco.webbff.dto.UsageSummary;
import com.telco.webbff.gateway.GatewayCustomer;
import com.telco.webbff.gateway.GatewayInvoice;
import com.telco.webbff.gateway.GatewayQuota;
import com.telco.webbff.gateway.GatewaySubscription;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Account/home/invoice composition (web-bff contract, ADR-022; feature 16.5.1). The Simple Service
 * Layer body behind the {@code /bff/v1/home}, {@code /bff/v1/account} and {@code /bff/v1/invoices}
 * endpoints: it fans out to gateway routes ({@code /api/v1/**}) server-side and shapes the result into
 * UI DTOs. It holds no state and owns no data; every read goes to the owning domain service through
 * the single {@link GatewayClient}, and the caller's bearer token is relayed automatically.
 *
 * <p><strong>Strict self-scoping (security-critical).</strong> Every read is scoped to the caller's
 * own resolved {@code customerId} - the identity-to-customer linkage claim populated on the JWT and
 * exposed via {@link CurrentUserProvider} (ADR-011). The BFF never accepts a client-supplied
 * {@code customerId}/{@code subscriptionId}: the endpoints bind no such parameter, so any value in the
 * query string or body is ignored and cannot widen scope. Downstream services additionally re-check
 * ownership against the same relayed identity, so a mismatched id would be rejected there too.
 */
@Service
public class AccountCompositionService {

    private static final ParameterizedTypeReference<ApiResult<GatewayCustomer>> CUSTOMER =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<PageResult<GatewaySubscription>>> SUBSCRIPTION_PAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<PageResult<GatewayInvoice>>> INVOICE_PAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<GatewayQuota>> QUOTA =
            new ParameterizedTypeReference<>() { };

    private static final String STATUS_ACTIVE = "ACTIVE";

    /** Upper bound for the subscription fan-out; a customer holds only a handful of subscriptions. */
    private static final int SUBSCRIPTION_PAGE_SIZE = 100;

    private final GatewayClient gateway;
    private final CurrentUserProvider currentUserProvider;

    public AccountCompositionService(GatewayClient gateway, CurrentUserProvider currentUserProvider) {
        this.gateway = gateway;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Composes the dashboard for {@code GET /bff/v1/home}: the caller's profile, their ACTIVE
     * subscriptions, and their single latest invoice, in one BFF call. The browser makes one request;
     * the profile/subscriptions/latest-invoice fan-out happens server-side. All three reads are scoped
     * to the caller's own resolved {@code customerId}.
     */
    public HomeResponse home() {
        String customerId = requireCustomerId();

        GatewayCustomer customer = data(gateway.get("/api/v1/customers/" + customerId, CUSTOMER));

        List<SubscriptionSummary> active = new ArrayList<>();
        for (GatewaySubscription subscription : subscriptions(customerId)) {
            if (STATUS_ACTIVE.equals(subscription.status())) {
                active.add(toSummary(subscription));
            }
        }

        // billing lists invoices newest-first (findByCustomerIdOrderByCreatedAtDesc), so page 0 size 1
        // is the latest invoice; a customer with no invoices yet yields null (a fresh-account state).
        List<GatewayInvoice> latest = page(gateway.get(
                "/api/v1/invoices?customerId=" + customerId + "&page=0&size=1", INVOICE_PAGE));
        InvoiceSummary latestInvoice = latest.isEmpty() ? null : toInvoiceSummary(latest.get(0));

        return new HomeResponse(toProfile(customer, customerId), active, latestInvoice);
    }

    /**
     * Composes the account view for {@code GET /bff/v1/account}: the caller's profile and every
     * subscription, each ACTIVE subscription carrying its current usage/quota (a per-subscription
     * usage-service read). Scoped to the caller's own resolved {@code customerId}; the per-subscription
     * quota is fetched by the subscription id returned from the caller's own subscription list, never
     * from a client-supplied id.
     */
    public AccountResponse account() {
        String customerId = requireCustomerId();

        GatewayCustomer customer = data(gateway.get("/api/v1/customers/" + customerId, CUSTOMER));

        List<AccountSubscription> rows = new ArrayList<>();
        for (GatewaySubscription subscription : subscriptions(customerId)) {
            UsageSummary usage = null;
            if (STATUS_ACTIVE.equals(subscription.status())) {
                GatewayQuota quota = data(gateway.get(
                        "/api/v1/usage/subscriptions/" + subscription.id() + "/quota", QUOTA));
                usage = toUsage(quota);
            }
            rows.add(new AccountSubscription(toSummary(subscription), usage));
        }

        return new AccountResponse(toProfile(customer, customerId), rows);
    }

    /**
     * Composes the paged invoice list for {@code GET /bff/v1/invoices}: the caller's invoices for the
     * requested page, each carrying the gateway PDF-download route, plus the paging metadata mirrored
     * from billing-service. Scoped to the caller's own resolved {@code customerId}.
     */
    public InvoicesResponse invoices(int page, int size) {
        String customerId = requireCustomerId();

        PageResult<GatewayInvoice> result = data(gateway.get(
                "/api/v1/invoices?customerId=" + customerId + "&page=" + page + "&size=" + size,
                INVOICE_PAGE));

        List<GatewayInvoice> content = result.content() == null ? List.of() : result.content();
        List<InvoiceSummary> invoices = new ArrayList<>(content.size());
        for (GatewayInvoice invoice : content) {
            invoices.add(toInvoiceSummary(invoice));
        }
        return new InvoicesResponse(invoices, result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }

    private List<GatewaySubscription> subscriptions(String customerId) {
        return page(gateway.get(
                "/api/v1/subscriptions?customerId=" + customerId
                        + "&page=0&size=" + SUBSCRIPTION_PAGE_SIZE,
                SUBSCRIPTION_PAGE));
    }

    /**
     * Resolves the caller's own {@code customerId} from the authenticated identity (the JWT
     * {@code customerId} linkage claim, ADR-011). This is the single source of scope for every read;
     * it is never taken from the request. An authenticated identity that is not yet linked to a
     * customer record cannot own account data, so the read is refused rather than widened.
     */
    private String requireCustomerId() {
        String customerId = currentUserProvider.currentUser().customerId();
        if (customerId == null || customerId.isBlank()) {
            throw new AccessDeniedException(
                    "authenticated identity is not linked to a customer record");
        }
        return customerId;
    }

    private static ProfileSummary toProfile(GatewayCustomer customer, String customerId) {
        return new ProfileSummary(customerId, fullName(customer), customer.status());
    }

    private static String fullName(GatewayCustomer customer) {
        String first = customer.firstName() == null ? "" : customer.firstName().trim();
        String last = customer.lastName() == null ? "" : customer.lastName().trim();
        return (first + " " + last).trim();
    }

    private static SubscriptionSummary toSummary(GatewaySubscription subscription) {
        return new SubscriptionSummary(
                subscription.id() == null ? null : subscription.id().toString(),
                subscription.msisdn(),
                subscription.tariffCode(),
                subscription.status());
    }

    private static UsageSummary toUsage(GatewayQuota quota) {
        return new UsageSummary(
                quota.mbTotal() - quota.mbRemaining(), quota.mbTotal(),
                quota.minutesTotal() - quota.minutesRemaining(), quota.minutesTotal(),
                quota.smsTotal() - quota.smsRemaining(), quota.smsTotal());
    }

    private static InvoiceSummary toInvoiceSummary(GatewayInvoice invoice) {
        String period = invoice.periodStart() == null
                ? null
                : YearMonth.from(invoice.periodStart().atZone(ZoneOffset.UTC)).toString();
        // Gateway route, not proxied bytes: billing streams the PDF at GET /api/v1/invoices/{id}/pdf
        // and does not expose a pre-signed object-store URL on the list response. The browser follows
        // this route through the gateway with its own token; the BFF never fetches the PDF (ADR-022).
        String pdfUrl = "/api/v1/invoices/" + invoice.id() + "/pdf";
        return new InvoiceSummary(
                invoice.id() == null ? null : invoice.id().toString(),
                period,
                invoice.grandTotal(),
                invoice.currency(),
                invoice.status(),
                pdfUrl);
    }

    private static <T> T data(ApiResult<T> result) {
        if (result == null || !result.success() || result.data() == null) {
            throw new DependencyFailureException("gateway returned an unsuccessful response", null);
        }
        return result.data();
    }

    private static <T> List<T> page(ApiResult<PageResult<T>> result) {
        PageResult<T> pageResult = data(result);
        return pageResult.content() == null ? List.of() : pageResult.content();
    }
}
