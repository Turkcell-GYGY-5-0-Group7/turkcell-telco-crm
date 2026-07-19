package com.telco.dispute.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive unit tests for the {@link Dispute} state machine (Sprint 22 Feature 22.2.1, ADR-028
 * Section 4): every legal transition and every illegal (state, transition-method) pair across the
 * 7 states x 6 transition methods (42 cases), plus the provisional-hold-adjacent guard on
 * {@link Dispute#resolveCustomer(BigDecimal, String)}'s {@code resolutionAmount}.
 */
class DisputeStateMachineTest {

    private static Dispute newDispute() {
        return Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "BILLING_ERROR", new BigDecimal("50.00"));
    }

    private static Dispute underReviewDispute() {
        Dispute d = newDispute();
        d.beginReview("agent-1");
        return d;
    }

    private static Dispute evidenceSubmittedDispute() {
        Dispute d = underReviewDispute();
        d.submitEvidence("customer-1", "receipt attached");
        return d;
    }

    private static Dispute resolvedCustomerDispute() {
        Dispute d = underReviewDispute();
        d.resolveCustomer(new BigDecimal("50.00"), "agent-1");
        return d;
    }

    private static Dispute resolvedMerchantDispute() {
        Dispute d = underReviewDispute();
        d.resolveMerchant("agent-1");
        return d;
    }

    private static Dispute withdrawnDispute() {
        Dispute d = newDispute();
        d.withdraw("customer-1");
        return d;
    }

    private static Dispute closedDispute() {
        Dispute d = resolvedCustomerDispute();
        d.close("agent-1");
        return d;
    }

    @Test
    void new_dispute_is_opened() {
        assertThat(newDispute().getStatus()).isEqualTo(DisputeStatus.OPENED);
    }

    @Test
    void create_with_neither_invoice_nor_payment_throws() {
        assertThatThrownBy(() ->
                Dispute.create(null, null, UUID.randomUUID(), "OTHER", BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- beginReview() ---

    @Test
    void beginReview_opened_transitions_to_under_review() {
        Dispute d = newDispute();
        d.beginReview("agent-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    }

    @Test
    void beginReview_evidence_submitted_transitions_to_under_review() {
        Dispute d = evidenceSubmittedDispute();
        d.beginReview("agent-2");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    }

    @Test
    void beginReview_is_illegal_from_under_review() {
        Dispute d = underReviewDispute();
        assertThatThrownBy(() -> d.beginReview("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void beginReview_is_illegal_from_resolved_customer() {
        Dispute d = resolvedCustomerDispute();
        assertThatThrownBy(() -> d.beginReview("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void beginReview_is_illegal_from_resolved_merchant() {
        Dispute d = resolvedMerchantDispute();
        assertThatThrownBy(() -> d.beginReview("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void beginReview_is_illegal_from_withdrawn() {
        Dispute d = withdrawnDispute();
        assertThatThrownBy(() -> d.beginReview("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void beginReview_is_illegal_from_closed() {
        Dispute d = closedDispute();
        assertThatThrownBy(() -> d.beginReview("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    // --- submitEvidence() ---

    @Test
    void submitEvidence_under_review_transitions_to_evidence_submitted() {
        Dispute d = underReviewDispute();
        d.submitEvidence("customer-1", "note");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.EVIDENCE_SUBMITTED);
    }

    @Test
    void submitEvidence_is_illegal_from_opened() {
        Dispute d = newDispute();
        assertThatThrownBy(() -> d.submitEvidence("customer-1", "note"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submitEvidence_is_illegal_from_evidence_submitted() {
        Dispute d = evidenceSubmittedDispute();
        assertThatThrownBy(() -> d.submitEvidence("customer-1", "note"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submitEvidence_is_illegal_from_resolved_customer() {
        Dispute d = resolvedCustomerDispute();
        assertThatThrownBy(() -> d.submitEvidence("customer-1", "note"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submitEvidence_is_illegal_from_resolved_merchant() {
        Dispute d = resolvedMerchantDispute();
        assertThatThrownBy(() -> d.submitEvidence("customer-1", "note"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submitEvidence_is_illegal_from_withdrawn() {
        Dispute d = withdrawnDispute();
        assertThatThrownBy(() -> d.submitEvidence("customer-1", "note"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submitEvidence_is_illegal_from_closed() {
        Dispute d = closedDispute();
        assertThatThrownBy(() -> d.submitEvidence("customer-1", "note"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- resolveCustomer(resolutionAmount) ---

    @Test
    void resolveCustomer_under_review_transitions_to_resolved_customer() {
        Dispute d = underReviewDispute();
        d.resolveCustomer(new BigDecimal("50.00"), "agent-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RESOLVED_CUSTOMER);
        assertThat(d.getResolutionAmount()).isEqualByComparingTo("50.00");
        assertThat(d.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveCustomer_is_illegal_from_opened() {
        Dispute d = newDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.TEN, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_is_illegal_from_evidence_submitted() {
        Dispute d = evidenceSubmittedDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.TEN, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_is_illegal_from_resolved_customer() {
        Dispute d = resolvedCustomerDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.TEN, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_is_illegal_from_resolved_merchant() {
        Dispute d = resolvedMerchantDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.TEN, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_is_illegal_from_withdrawn() {
        Dispute d = withdrawnDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.TEN, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_is_illegal_from_closed() {
        Dispute d = closedDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.TEN, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_null_amount_throws() {
        Dispute d = underReviewDispute();
        assertThatThrownBy(() -> d.resolveCustomer(null, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_zero_amount_throws() {
        Dispute d = underReviewDispute();
        assertThatThrownBy(() -> d.resolveCustomer(BigDecimal.ZERO, "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveCustomer_negative_amount_throws() {
        Dispute d = underReviewDispute();
        assertThatThrownBy(() -> d.resolveCustomer(new BigDecimal("-1.00"), "agent-1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- resolveMerchant() ---

    @Test
    void resolveMerchant_under_review_transitions_to_resolved_merchant() {
        Dispute d = underReviewDispute();
        d.resolveMerchant("agent-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RESOLVED_MERCHANT);
        assertThat(d.getResolutionAmount()).isNull();
        assertThat(d.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveMerchant_is_illegal_from_opened() {
        Dispute d = newDispute();
        assertThatThrownBy(() -> d.resolveMerchant("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveMerchant_is_illegal_from_evidence_submitted() {
        Dispute d = evidenceSubmittedDispute();
        assertThatThrownBy(() -> d.resolveMerchant("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveMerchant_is_illegal_from_resolved_customer() {
        Dispute d = resolvedCustomerDispute();
        assertThatThrownBy(() -> d.resolveMerchant("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveMerchant_is_illegal_from_resolved_merchant() {
        Dispute d = resolvedMerchantDispute();
        assertThatThrownBy(() -> d.resolveMerchant("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveMerchant_is_illegal_from_withdrawn() {
        Dispute d = withdrawnDispute();
        assertThatThrownBy(() -> d.resolveMerchant("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolveMerchant_is_illegal_from_closed() {
        Dispute d = closedDispute();
        assertThatThrownBy(() -> d.resolveMerchant("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    // --- withdraw() ---

    @Test
    void withdraw_opened_transitions_to_withdrawn() {
        Dispute d = newDispute();
        d.withdraw("customer-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.WITHDRAWN);
    }

    @Test
    void withdraw_under_review_transitions_to_withdrawn() {
        Dispute d = underReviewDispute();
        d.withdraw("customer-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.WITHDRAWN);
    }

    @Test
    void withdraw_is_illegal_from_evidence_submitted() {
        Dispute d = evidenceSubmittedDispute();
        assertThatThrownBy(() -> d.withdraw("customer-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withdraw_is_illegal_from_resolved_customer() {
        Dispute d = resolvedCustomerDispute();
        assertThatThrownBy(() -> d.withdraw("customer-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withdraw_is_illegal_from_resolved_merchant() {
        Dispute d = resolvedMerchantDispute();
        assertThatThrownBy(() -> d.withdraw("customer-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withdraw_is_illegal_from_withdrawn() {
        Dispute d = withdrawnDispute();
        assertThatThrownBy(() -> d.withdraw("customer-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withdraw_is_illegal_from_closed() {
        Dispute d = closedDispute();
        assertThatThrownBy(() -> d.withdraw("customer-1")).isInstanceOf(BusinessRuleException.class);
    }

    // --- close() ---

    @Test
    void close_resolved_customer_transitions_to_closed() {
        Dispute d = resolvedCustomerDispute();
        d.close("agent-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.CLOSED);
        assertThat(d.getClosedAt()).isNotNull();
    }

    @Test
    void close_resolved_merchant_transitions_to_closed() {
        Dispute d = resolvedMerchantDispute();
        d.close("agent-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.CLOSED);
    }

    @Test
    void close_withdrawn_transitions_to_closed() {
        Dispute d = withdrawnDispute();
        d.close("customer-1");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.CLOSED);
    }

    @Test
    void close_is_illegal_from_opened() {
        Dispute d = newDispute();
        assertThatThrownBy(() -> d.close("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void close_is_illegal_from_under_review() {
        Dispute d = underReviewDispute();
        assertThatThrownBy(() -> d.close("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void close_is_illegal_from_evidence_submitted() {
        Dispute d = evidenceSubmittedDispute();
        assertThatThrownBy(() -> d.close("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void close_is_illegal_from_closed() {
        Dispute d = closedDispute();
        assertThatThrownBy(() -> d.close("agent-1")).isInstanceOf(BusinessRuleException.class);
    }

    // --- DisputeStateHistory audit trail ---

    @Test
    void every_successful_transition_appends_exactly_one_history_row() {
        Dispute d = newDispute();
        d.beginReview("agent-1");
        d.submitEvidence("customer-1", "more proof");
        d.beginReview("agent-2");
        d.resolveCustomer(new BigDecimal("50.00"), "agent-2");
        d.close("agent-2");

        assertThat(d.getHistory()).hasSize(5);
        assertThat(d.getHistory().get(0).getToStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(d.getHistory().get(1).getToStatus()).isEqualTo(DisputeStatus.EVIDENCE_SUBMITTED);
        assertThat(d.getHistory().get(2).getToStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(d.getHistory().get(3).getToStatus()).isEqualTo(DisputeStatus.RESOLVED_CUSTOMER);
        assertThat(d.getHistory().get(4).getToStatus()).isEqualTo(DisputeStatus.CLOSED);
    }
}
