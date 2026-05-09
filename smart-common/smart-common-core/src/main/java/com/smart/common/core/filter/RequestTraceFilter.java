package com.smart.common.core.filter;

import com.smart.common.core.auth.AuthHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Initialises the distributed trace-id in MDC from an incoming header,
 * or generates one if absent.  Runs at highest precedence so every
 * subsequent log statement includes the trace-id.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String traceId = request.getHeader(AuthHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }

        MDC.put(MDC_KEY, traceId);
        response.setHeader(AuthHeaders.TRACE_ID, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Generate a compact 32-char hex trace-id using ThreadLocalRandom
     * (faster than UUID.randomUUID for high-throughput scenarios).
     */
    private static String generateTraceId() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return Long.toHexString(rng.nextLong()) + Long.toHexString(rng.nextLong());
    }
}
