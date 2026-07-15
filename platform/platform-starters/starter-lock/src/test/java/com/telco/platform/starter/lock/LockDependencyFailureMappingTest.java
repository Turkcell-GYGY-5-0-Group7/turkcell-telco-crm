package com.telco.platform.starter.lock;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.LockErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the ADR-024 Section 5 fail-closed contract end to end (feature 17.1.3): a
 * {@link DependencyFailureException} built with {@link LockErrorCode#LOCK_ACQUISITION_FAILED}
 * maps to HTTP 503 with a stable {@code ApiError.code} via starter-api's EXISTING
 * {@code GlobalExceptionHandler.handleDependencyFailure} - no handler code was changed to make this
 * pass; {@code starter-lock} only had to be on the classpath as a test dependency.
 *
 * <p>{@code telco.platform.lock.enabled=false} keeps this test independent of a running Redis: it is
 * verifying the HTTP mapping, not lock acquisition itself (that is 17.2.2's job, against a real Redis
 * Testcontainer).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = LockDependencyFailureMappingTest.TestApp.class,
        properties = "telco.platform.lock.enabled=false")
@AutoConfigureMockMvc
class LockDependencyFailureMappingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lockAcquisitionFailureMapsTo503WithStableErrorCode() throws Exception {
        mockMvc.perform(get("/test/lock-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value(LockErrorCode.LOCK_ACQUISITION_FAILED.code()));
    }

    @Configuration
    @EnableAutoConfiguration
    @Import(ThrowingController.class)
    static class TestApp {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/lock-failure")
        public void fail() {
            throw new DependencyFailureException(
                    LockErrorCode.LOCK_ACQUISITION_FAILED,
                    "Failed to acquire distributed lock [test-key]",
                    Map.of("lockKey", "test-key"),
                    null);
        }
    }
}
