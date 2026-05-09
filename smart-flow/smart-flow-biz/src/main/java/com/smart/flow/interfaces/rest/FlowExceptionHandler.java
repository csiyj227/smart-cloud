package com.smart.flow.interfaces.rest;

import com.smart.common.core.web.ApiResult;
import com.smart.flow.api.exception.FlowChartCompileException;
import com.smart.flow.domain.form.FormFieldRuleViolationException;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Module-local exception advice that supplements
 * {@code com.smart.common.security.component.GlobalExceptionHandler}.
 *
 * <p>Why a separate advice rather than extending the global one? The global handler lives in
 * {@code smart-common-security} which is a leaf module - it cannot depend on workflow types.
 * Putting flow-specific mappings here keeps the dependency graph one-way and means the same
 * upgraded handler does not need a global rebuild every time a new flow exception is added.
 *
 * <p>{@link FormFieldRuleViolationException} is mapped to a structured payload that includes
 * the offending field and rule, so the front-end can highlight the exact input rather than
 * showing a generic "submission rejected" toast. {@link FlowChartCompileException} is mapped
 * with the full issue list so the designer can render every red squiggle in one round-trip.
 *
 * <p>Order matters: this advice does NOT swallow {@link IllegalArgumentException} /
 * {@link IllegalStateException} - those propagate to the global handler's catch-all which
 * already produces a sane {@link R} envelope. We only add mappings the global handler does
 * not know about.
 */
@Slf4j
// HIGHEST_PRECEDENCE so this advice's per-exception handlers are consulted before the global
// catch-all in smart-common-security. Without this the global @ExceptionHandler(Exception.class)
// can shadow our specialised mappings (e.g. FlowChartCompileException, which is just a
// RuntimeException - the global handler would happily catch it and return a generic 500).
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.smart.flow")
public class FlowExceptionHandler {

    /** Stable error code so the front-end can switch on it without parsing the message. */
    public static final int CODE_FIELD_RULE_VIOLATION = 41001;
    public static final int CODE_CHART_COMPILE_FAIL = 41002;
    public static final int CODE_FLOWABLE_NOT_FOUND = 41003;
    public static final int CODE_FLOWABLE_ENGINE = 50001;

    @ExceptionHandler(FormFieldRuleViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Map<String, Object>> handleFieldRuleViolation(FormFieldRuleViolationException e) {
        log.warn("Field rule violation: field={} rule={} reason={}",
                e.getField(), e.getRule(), e.getReason());
        // The shared ApiResult<T> only has failure(int, String) / failure(String); we want both a
        // structured payload AND a custom code, so we build the envelope ourselves with the
        // setters (still a typed ApiResult<Map<String,Object>>, no raw types).
        // LinkedHashMap keeps the JSON keys in a stable order - easier to grep in logs.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("field", e.getField());
        payload.put("rule", e.getRule().getWire());
        payload.put("reason", e.getReason());
        return buildFailed(CODE_FIELD_RULE_VIOLATION, "字段规则校验失败: " + e.getReason(), payload);
    }

    @ExceptionHandler(FlowChartCompileException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Map<String, Object>> handleCompile(FlowChartCompileException e) {
        log.warn("Chart compile failed: {} issue(s) - {}", e.getIssues().size(), e.getMessage());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issues", e.getIssues());
        return buildFailed(CODE_CHART_COMPILE_FAIL, "流程图校验失败", payload);
    }

    @ExceptionHandler(FlowableObjectNotFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleFlowableNotFound(FlowableObjectNotFoundException e) {
        log.warn("Flowable object not found: {}", e.getMessage());
        return ApiResult.failure("FLOW_NOT_FOUND", "流程对象不存在或已结束");
    }

    @ExceptionHandler(FlowableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleFlowable(FlowableException e) {
        log.error("Flowable engine error", e);
        return ApiResult.failure("FLOW_ENGINE_ERROR", "流程引擎内部错误");
    }

    /**
     * Build a failure ApiResult with a structured payload.
     * Uses ApiResult.builder() to include both errorCode and payload data.
     */
    private static <T> ApiResult<T> buildFailed(int code, String msg, T data) {
        return ApiResult.<T>builder()
                .success(false)
                .errorCode("FLOW_" + code)
                .msg(msg)
                .data(data)
                .build();
    }
}
