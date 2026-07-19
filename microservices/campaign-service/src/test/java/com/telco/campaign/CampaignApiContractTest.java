package com.telco.campaign;

import com.telco.campaign.api.CampaignController;
import com.telco.campaign.api.CampaignInternalController;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.application.dto.CampaignValidationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-side API contract gate for campaign-service (Feature 21.5.2), mirroring
 * {@code TariffApiContractTest}: no Spring context needed, pure reflection against the response DTOs
 * and controller mapping annotations, checked against {@code docs/api-contracts/campaign-service.md}.
 *
 * <p>Two things this test specifically guards, both called out in the current contract doc:
 * <ol>
 *   <li>{@code POST /internal/campaigns/validate} is tokenless and lives under {@code /internal},
 *       NOT {@code /api/v1/campaigns/validate} (ADR-027 second ratification addendum, tech-lead
 *       ruling 2026-07-13) - a regression back to the old path would silently break order-service's
 *       {@code CampaignServiceClient}, which targets {@code /internal/campaigns/validate} explicitly.</li>
 *   <li>The admin lifecycle surface stays under {@code /api/v1/campaigns/**}.</li>
 * </ol>
 *
 * <p>Response DTO field checks are a subset assertion (adding fields is allowed; removing/renaming
 * one is a breaking change and fails this test).
 */
class CampaignApiContractTest {

    private static final Set<String> CAMPAIGN_RESPONSE_REQUIRED_FIELDS = Set.of(
            "id", "code", "name", "description", "discountType", "discountValue",
            "applicableTariffCodes", "validFrom", "validTo", "status",
            "totalRedemptionCap", "perCustomerRedemptionCap",
            "createdAt", "updatedAt", "version",
            "staleTariffFlag", "staleTariffReason", "staleTariffFlaggedAt");

    private static final Set<String> CAMPAIGN_VALIDATION_RESPONSE_REQUIRED_FIELDS = Set.of(
            "eligible", "campaignId", "discountType", "discountValue", "reason");

    @Test
    void campaign_response_exposes_every_field_documented_in_the_api_contract() {
        Set<String> fields = recordComponentNames(CampaignResponse.class);

        assertThat(fields)
                .as("CampaignResponse must keep every field documented in "
                        + "docs/api-contracts/campaign-service.md's admin endpoints (GET/POST "
                        + "/api/v1/campaigns/**) - a removed/renamed field is a breaking API change")
                .containsAll(CAMPAIGN_RESPONSE_REQUIRED_FIELDS);
    }

    @Test
    void campaign_validation_response_exposes_every_field_documented_in_the_api_contract() {
        Set<String> fields = recordComponentNames(CampaignValidationResponse.class);

        assertThat(fields)
                .as("CampaignValidationResponse must keep every field documented in "
                        + "docs/api-contracts/campaign-service.md's POST /internal/campaigns/validate "
                        + "contract - order-service's CampaignServiceClient binds all five")
                .containsAll(CAMPAIGN_VALIDATION_RESPONSE_REQUIRED_FIELDS);
    }

    @Test
    void internal_validate_endpoint_is_mounted_under_internal_not_api_v1() {
        String classPath = requiredRequestMapping(CampaignInternalController.class);
        assertThat(classPath)
                .as("CampaignInternalController must stay mounted under /internal, tokenless "
                        + "(ADR-027 second ratification addendum) - not /api/v1")
                .isEqualTo("/internal/campaigns");

        Method validate = findMethod(CampaignInternalController.class, "validate",
                com.telco.campaign.application.dto.CampaignValidationRequest.class);
        PostMapping postMapping = validate.getAnnotation(PostMapping.class);
        assertThat(postMapping).as("validate() must be a POST mapping").isNotNull();
        assertThat(postMapping.value()).containsExactly("/validate");

        String fullPath = classPath + postMapping.value()[0];
        assertThat(fullPath).isEqualTo("/internal/campaigns/validate");
    }

    @Test
    void admin_campaign_lifecycle_endpoints_are_mounted_under_api_v1_campaigns() {
        String classPath = requiredRequestMapping(CampaignController.class);
        assertThat(classPath).isEqualTo("/api/v1/campaigns");

        Method create = findMethod(CampaignController.class, "createCampaign",
                com.telco.campaign.application.dto.CreateCampaignRequest.class);
        assertThat(create.getAnnotation(PostMapping.class)).isNotNull();

        Method activate = findMethod(CampaignController.class, "activateCampaign", java.util.UUID.class);
        assertThat(activate.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/activate");

        Method pause = findMethod(CampaignController.class, "pauseCampaign", java.util.UUID.class);
        assertThat(pause.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/pause");

        Method cancel = findMethod(CampaignController.class, "cancelCampaign", java.util.UUID.class);
        assertThat(cancel.getAnnotation(DeleteMapping.class).value()).containsExactly("/{id}");

        Method get = findMethod(CampaignController.class, "getCampaign", java.util.UUID.class);
        assertThat(get.getAnnotation(GetMapping.class).value()).containsExactly("/{id}");

        Method list = findMethod(CampaignController.class, "listCampaigns", int.class, int.class);
        assertThat(list.getAnnotation(GetMapping.class)).isNotNull();
    }

    // --- helpers ---

    private static Set<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static String requiredRequestMapping(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertThat(mapping).as(controller.getSimpleName() + " must carry a class-level @RequestMapping")
                .isNotNull();
        assertThat(mapping.value()).hasSize(1);
        return mapping.value()[0];
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... paramTypes) {
        try {
            return type.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Expected method " + name + " on " + type.getSimpleName()
                    + " - the API contract has drifted (endpoint renamed or removed)", e);
        }
    }
}
