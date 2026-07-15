package com.telco.webbff.client;

import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVIDENCE for the error mapping in {@link GatewayClient}: what does a platform service ACTUALLY
 * answer when a multipart upload exceeds {@code spring.servlet.multipart.max-file-size}?
 *
 * <p>This is the exact question behind the onboarding defect: customer-service's KYC upload
 * (multipart) blew its size limit, web-bff reported 503 DEPENDENCY_FAILURE, and the browser showed a
 * bare "Failed to fetch". Rather than assume a 413, this test reproduces the condition on a REAL
 * Tomcat running the REAL platform stack (starter-api's {@code GlobalExceptionHandler} - the same
 * advice customer-service inherits; it declares no advice of its own) against a test-only multipart
 * endpoint shaped like {@code CustomerDocumentController}, with the multipart limit lowered to 1 KB.
 *
 * <p>Result (asserted below): the overflow surfaces as <b>500 INTERNAL_ERROR</b>, not 413. The
 * platform advice's {@code @ExceptionHandler(Exception.class)} catch-all runs in
 * {@code ExceptionHandlerExceptionResolver}, which precedes Spring's
 * {@code DefaultHandlerExceptionResolver}, so {@code MaxUploadSizeExceededException} never reaches
 * the framework's own 413 mapping. That 500 is what {@link GatewayClient}'s {@code default} arm
 * turned into a {@code DependencyFailureException} (503) - the misleading status the browser saw.
 *
 * <p>Consequences, both implemented:
 * <ul>
 *   <li>web-bff cannot distinguish that 500 from any other downstream 500, so it must NOT guess:
 *       the oversize document is rejected UP FRONT by
 *       {@code OnboardingCompositionService#checkSize} (400 VALIDATION_FAILED, before any customer
 *       is registered). That is the honest client error.</li>
 *   <li>{@link GatewayClient} additionally maps a genuine 413 to a 4xx, for any hop that does report
 *       payload-too-large honestly (gateway/proxy) - see {@link GatewayErrorMappingTest}.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "telco.gateway.base-url=http://gateway.mock",
                "telco.platform.security.gateway-trust.enabled=false",
                "telco.platform.security.jwt.secret=d2ViLWJmZi10ZXN0LXNlY3JldC1rZXktZm9yLXRlc3RpbmctMjAyNg==",
                "telco.platform.security.jwt.issuer=telco",
                // The condition under test: a deliberately tiny multipart limit, so a 4 KB part overflows.
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=2KB"
        }
)
@ActiveProfiles("test")
class MultipartOverflowContractTest {

    @Autowired
    private JwtService jwtService;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String token;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();
        token = jwtService.issue(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"));
    }

    @Test
    void a_multipart_upload_over_the_limit_is_answered_500_INTERNAL_ERROR_not_413() {
        ResponseEntity<String> response = upload(4096);

        // The measured truth. NOT 413. This is why web-bff must reject an oversize KYC document
        // itself rather than trying to interpret customer-service's status.
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("INTERNAL_ERROR");
    }

    @Test
    void a_multipart_upload_within_the_limit_succeeds() {
        ResponseEntity<String> response = upload(512);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("512");
    }

    private ResponseEntity<String> upload(int bytes) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("type", "ID_CARD");
        parts.add("file", new ByteArrayResource(new byte[bytes]) {
            @Override
            public String getFilename() {
                return "id.png";
            }
        });

        return client.post()
                .uri("/__test/multipart")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .toEntity(String.class);
    }

    /**
     * A stand-in for customer-service's {@code CustomerDocumentController}: same signature
     * ({@code @RequestParam("type")} + {@code @RequestParam("file") MultipartFile}), so multipart
     * resolution fails at exactly the same point. Test-only - registered by this test class alone,
     * never part of the web-bff application.
     */
    @TestConfiguration
    @RestController
    @RequestMapping("/__test")
    static class MultipartProbeController {

        @PostMapping("/multipart")
        String upload(@RequestParam("type") String type, @RequestParam("file") MultipartFile file) {
            return "{\"type\":\"" + type + "\",\"size\":" + file.getSize() + "}";
        }
    }
}
