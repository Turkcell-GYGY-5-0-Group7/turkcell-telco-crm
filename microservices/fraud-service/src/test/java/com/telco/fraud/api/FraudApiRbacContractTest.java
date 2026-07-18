package com.telco.fraud.api;

import com.telco.fraud.application.dto.ResolveFraudCaseRequest;
import com.telco.fraud.application.dto.UpdateFraudRuleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-side RBAC contract gate for the Feature 23.3 fraud API, mirroring campaign-service's
 * {@code CampaignApiContractTest} (pure reflection, no Spring context / no Docker). It pins the exact
 * {@code @PreAuthorize} role expression on each of the five routes so a later edit that widens or drops
 * a role fails the build:
 *
 * <ul>
 *   <li>fraud-case list/view/resolve require the agent/fraud-analyst role ({@code SUPPORT}) or
 *       {@code ADMIN} - fraud data is sensitive, not customer-self-service.</li>
 *   <li>fraud-rule read is available to the agent role; fraud-rule write ({@code PUT}) is gated on the
 *       stricter {@code ADMIN} role alone (ADR-029 Section 4).</li>
 * </ul>
 *
 * <p>The {@code SUPPORT}/{@code ADMIN} names are the platform's existing role taxonomy (ticket-service
 * gates its agent assign/resolve endpoints on {@code hasRole('ADMIN') or hasRole('SUPPORT')}); no new
 * role name is invented here.
 */
class FraudApiRbacContractTest {

    private static final String AGENT_RULE = "hasRole('SUPPORT') or hasRole('ADMIN')";
    private static final String ADMIN_RULE = "hasRole('ADMIN')";

    @Test
    void fraud_cases_are_mounted_under_api_v1_and_gated_on_the_agent_role() {
        assertThat(requestMapping(FraudCaseController.class)).isEqualTo("/api/v1/fraud-cases");

        assertThat(preAuthorize(method(FraudCaseController.class, "listCases",
                com.telco.fraud.domain.FraudCaseStatus.class, UUID.class, int.class, int.class)))
                .isEqualTo(AGENT_RULE);
        assertThat(preAuthorize(method(FraudCaseController.class, "getCase", UUID.class)))
                .isEqualTo(AGENT_RULE);
        assertThat(preAuthorize(method(FraudCaseController.class, "resolveCase",
                UUID.class, ResolveFraudCaseRequest.class)))
                .isEqualTo(AGENT_RULE);
    }

    @Test
    void fraud_rules_are_mounted_under_api_v1_with_admin_write_and_agent_read() {
        assertThat(requestMapping(FraudRuleController.class)).isEqualTo("/api/v1/fraud-rules");

        assertThat(preAuthorize(method(FraudRuleController.class, "listRules")))
                .isEqualTo(AGENT_RULE);
        assertThat(preAuthorize(method(FraudRuleController.class, "updateRule",
                String.class, UpdateFraudRuleRequest.class)))
                .isEqualTo(ADMIN_RULE);
    }

    // --- helpers ---

    private static String requestMapping(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertThat(mapping).as(controller.getSimpleName() + " must carry a class-level @RequestMapping")
                .isNotNull();
        assertThat(mapping.value()).hasSize(1);
        return mapping.value()[0];
    }

    private static String preAuthorize(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(method.getName() + " must carry @PreAuthorize (RBAC, ADR-011)")
                .isNotNull();
        return annotation.value();
    }

    private static Method method(Class<?> type, String name, Class<?>... paramTypes) {
        try {
            return type.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Expected method " + name + " on " + type.getSimpleName()
                    + " - the fraud API contract has drifted (endpoint renamed or removed)", e);
        }
    }
}
