package com.telco.fraud.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudCaseTest {

    private FraudCase openCase() {
        return new FraudCase(UUID.randomUUID(), UUID.randomUUID(), FraudCaseStatus.OPEN,
                new ArrayList<>(List.of(UUID.randomUUID())), Instant.now(), null, null);
    }

    @Test
    void resolve_confirms_and_stamps_resolver() {
        FraudCase fraudCase = openCase();
        Instant now = Instant.now();

        fraudCase.resolve(FraudCaseStatus.CONFIRMED, "agent-1", now);

        assertThat(fraudCase.getStatus()).isEqualTo(FraudCaseStatus.CONFIRMED);
        assertThat(fraudCase.getResolvedBy()).isEqualTo("agent-1");
        assertThat(fraudCase.getResolvedAt()).isEqualTo(now);
    }

    @Test
    void resolve_rejects_a_non_terminal_outcome() {
        FraudCase fraudCase = openCase();

        assertThatThrownBy(() -> fraudCase.resolve(FraudCaseStatus.UNDER_REVIEW, "agent-1", Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolve_rejects_an_already_resolved_case() {
        FraudCase fraudCase = openCase();
        fraudCase.resolve(FraudCaseStatus.DISMISSED, "agent-1", Instant.now());

        assertThatThrownBy(() -> fraudCase.resolve(FraudCaseStatus.CONFIRMED, "agent-2", Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }
}
