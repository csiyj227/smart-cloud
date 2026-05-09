package com.smart.common.core.auth;

/**
 * HTTP header names and values used for authentication,
 * inter-service communication, and distributed tracing.
 *
 * <p>Grouped by concern rather than a flat constant list.
 */
public final class AuthHeaders {

    private AuthHeaders() {
    }

    // ── inter-service call identification ──────────────────────

    /** Header injected by Feign and stripped by gateway for internal calls. */
    public static final String SERVICE_CALL = "X-Internal-Call";
    public static final String SERVICE_CALL_PRESENT = "true";

    // ── identity propagation ──────────────────────────────────

    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String USER_ID   = "X-User-Id";
    public static final String USERNAME  = "X-Username";
    public static final String CLIENT_ID = "X-Client-Id";

    // ── distributed tracing ───────────────────────────────────

    public static final String TRACE_ID = "X-Trace-Id";

    // ── OAuth2 ────────────────────────────────────────────────

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String TOKEN_ENDPOINT = "/oauth2/token";
}