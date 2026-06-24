package com.telco.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Fixed-window rate limiter: 100 requests per minute per authenticated user (keyed by
 * JWT subject), or per client IP for unauthenticated allowlisted routes (NFR-18).
 * Uses an atomic Lua script to increment a Redis counter and set TTL on first call.
 */
@Component
@Order(20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final int LIMIT = 100;
    private static final long WINDOW_SECONDS = 60L;

    private static final String LUA =
            "local c = redis.call('INCR', KEYS[1])\n" +
            "if c == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return c";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> rateLimitScript;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.rateLimitScript = RedisScript.of(LUA, Long.class);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        try {
            String key = resolveKey(request);
            Long count = redis.execute(rateLimitScript, List.of(key), String.valueOf(WINDOW_SECONDS));

            if (count != null && count > LIMIT) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "error", Map.of(
                                "code", "RATE_LIMIT_EXCEEDED",
                                "message", "Rate limit exceeded: " + LIMIT + " requests per minute"
                        )
                )));
                return;
            }
        } catch (Exception e) {
            // Redis unavailable: fail open so a Redis outage does not take down the gateway.
            logger.warn("Rate-limit check skipped: Redis unavailable - {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String subject = jwtAuth.getToken().getSubject();
            if (subject != null && !subject.isBlank()) {
                return "rate-limit:user:" + subject;
            }
        }
        return "rate-limit:ip:" + request.getRemoteAddr();
    }
}
