package com.telco.gateway.filter;

import com.telco.gateway.support.MutableHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates an X-Correlation-Id when the client does not supply one, propagates it
 * downstream (via MutableHttpServletRequest), and echoes it on the response (NFR-13).
 * Runs at the highest priority so every subsequent filter and the gateway route handler
 * observe a correlation ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.putHeader(CORRELATION_ID_HEADER, correlationId);

        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        chain.doFilter(mutable, response);
    }
}
