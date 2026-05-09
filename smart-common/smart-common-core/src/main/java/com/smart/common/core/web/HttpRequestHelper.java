package com.smart.common.core.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.Optional;

/**
 * Convenience accessors for the current {@link HttpServletRequest}.
 */
public final class HttpRequestHelper {

    private HttpRequestHelper() {
    }

    public static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.requireNonNull(attrs, "No active HTTP request").getRequest();
    }

    public static Optional<String> header(String name) {
        return Optional.ofNullable(currentRequest().getHeader(name));
    }

    /**
     * Resolve client IP considering reverse-proxy headers.
     */
    public static String clientIp() {
        HttpServletRequest req = currentRequest();
        String ip = firstNonEmpty(
                req.getHeader("X-Forwarded-For"),
                req.getHeader("X-Real-IP"),
                req.getHeader("Proxy-Client-IP"),
                req.getRemoteAddr()
        );
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    public static String requestUri() {
        return currentRequest().getRequestURI();
    }

    public static String requestUrl() {
        return currentRequest().getRequestURL().toString();
    }

    public static String httpMethod() {
        return currentRequest().getMethod();
    }

    private static String firstNonEmpty(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isEmpty() && !"unknown".equalsIgnoreCase(c)) {
                return c;
            }
        }
        return null;
    }
}
