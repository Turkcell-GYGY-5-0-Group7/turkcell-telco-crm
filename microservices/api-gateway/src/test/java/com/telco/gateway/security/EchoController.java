package com.telco.gateway.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-only protected endpoint. It is not a configured gateway route, so the request is handled
 * locally instead of proxied, letting the integration test observe the identity headers that
 * JwtClaimsFilter injects downstream.
 */
@RestController
class EchoController {

    @GetMapping("/api/v1/echo")
    Map<String, String> echo(@RequestHeader(value = "X-User-Id", required = false) String userId,
                             @RequestHeader(value = "X-User-Roles", required = false) String roles,
                             @RequestHeader(value = "X-Customer-Id", required = false) String customerId) {
        Map<String, String> body = new HashMap<>();
        body.put("userId", userId);
        body.put("roles", roles);
        body.put("customerId", customerId);
        return body;
    }
}
