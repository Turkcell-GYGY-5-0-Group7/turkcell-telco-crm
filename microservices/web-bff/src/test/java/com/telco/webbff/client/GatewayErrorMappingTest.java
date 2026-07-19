package com.telco.webbff.client;

import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Status mapping of {@link GatewayClient}. The defect this pins down: a downstream rejection of an
 * oversize upload was reported to the browser as 503 DEPENDENCY_FAILURE ("the platform is broken")
 * even though the caller had simply sent too many bytes. 413 PAYLOAD_TOO_LARGE is a CLIENT error and
 * must surface as a 4xx the UI can show.
 *
 * <p>What customer-service really returns for a multipart overflow (500, not 413) is proved
 * separately by {@link MultipartOverflowContractTest}; that is why the oversize KYC document is also
 * rejected up front in {@code OnboardingCompositionService} rather than being inferred from a status.
 */
class GatewayErrorMappingTest {

    private static final ParameterizedTypeReference<String> STRING = new ParameterizedTypeReference<>() { };

    private GatewayClient gateway;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.mock");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GatewayClient(builder.build());
    }

    @Test
    void payload_too_large_maps_to_a_client_validation_error_not_a_dependency_failure() {
        server.expect(requestTo("http://gateway.mock/api/v1/customers/c-1/documents"))
                .andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE));

        assertThatThrownBy(() -> gateway.postMultipart("/api/v1/customers/c-1/documents", parts(), STRING))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).code())
                        .isEqualTo(CommonErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("payload too large");
        server.verify();
    }

    @Test
    void a_genuine_downstream_failure_still_maps_to_a_dependency_failure() {
        server.expect(requestTo("http://gateway.mock/api/v1/customers"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> gateway.get("/api/v1/customers", STRING))
                .isInstanceOf(DependencyFailureException.class);
        server.verify();
    }

    @Test
    void a_downstream_bad_request_still_maps_to_a_validation_error() {
        server.expect(requestTo("http://gateway.mock/api/v1/customers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> gateway.get("/api/v1/customers", STRING))
                .isInstanceOf(ValidationException.class);
        server.verify();
    }

    private static MultiValueMap<String, Object> parts() {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("type", "ID_CARD");
        parts.add("file", new ByteArrayResource("bytes".getBytes()) {
            @Override
            public String getFilename() {
                return "id.png";
            }
        });
        return parts;
    }
}
