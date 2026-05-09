package com.smart.common.core.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.MDC;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Standard API response envelope.
 *
 * <p>Uses {@code success} flag instead of numeric code to clearly indicate outcome.
 * {@code errorCode} is a domain-specific string (e.g. "USER_NOT_FOUND") that only
 * appears on failure, giving clients a machine-readable error identifier.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String msg;
    private final T data;
    private final String errorCode;
    private final String traceId;
    @Builder.Default
    private final long timestamp = Instant.now().toEpochMilli();

    // ── front-end compatibility ────────────────────────────────
    // The UI reads { code, msg, data }. These computed getters
    // bridge the gap so no front-end changes are needed.

    /** Returns 0 on success, 1 on failure — keeps the front-end {@code if (code !== 0)} check working. */
    public int getCode() {
        return success ? 0 : 1;
    }

    /** Alias of {@link #getMsg()} for front-end compatibility ({@code data.msg}). */
    public String getMsg() {
        return msg;
    }

    // ── success factories ──────────────────────────────────────

    public static <T> ApiResult<T> success() {
        return ApiResult.<T>builder()
                .success(true)
                .msg("ok")
                .traceId(currentTraceId())
                .build();
    }

    public static <T> ApiResult<T> success(T payload) {
        return ApiResult.<T>builder()
                .success(true)
                .msg("ok")
                .data(payload)
                .traceId(currentTraceId())
                .build();
    }

    public static <T> ApiResult<T> success(T payload, String message) {
        return ApiResult.<T>builder()
                .success(true)
                .msg(message)
                .data(payload)
                .traceId(currentTraceId())
                .build();
    }

    // ── failure factories ──────────────────────────────────────

    public static <T> ApiResult<T> failure(String message) {
        return ApiResult.<T>builder()
                .success(false)
                .msg(message)
                .traceId(currentTraceId())
                .build();
    }

    public static <T> ApiResult<T> failure(String errorCode, String message) {
        return ApiResult.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .msg(message)
                .traceId(currentTraceId())
                .build();
    }

    // ── helpers ────────────────────────────────────────────────

    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}