package com.smart.common.security.component;

import com.smart.common.core.exception.CaptchaException;
import com.smart.common.core.exception.BusinessException;
import com.smart.common.core.util.I18nUtil;
import com.smart.common.core.web.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler — converts uncaught exceptions into unified R<T> responses.
 * Placed in smart-common-security so all resource-server modules inherit it automatically.
 *
 * 全局异常处理器，将未捕获的异常转换为统一的 R&lt;T&gt; 响应格式。
 * 放置在 smart-common-security 模块中，使所有资源服务器模块自动继承该异常处理能力。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: [{}] {}", e.getErrorCode(), e.getMessage());
        return ApiResult.failure(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(CaptchaException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleCaptchaException(CaptchaException e) {
        log.warn("CaptchaException: {}", e.getMessage());
        return ApiResult.failure(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResult.failure(msg);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<Void> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException e) {
        return ApiResult.failure("ACCESS_DENIED", I18nUtil.get("error.access.denied"));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ApiResult.failure(I18nUtil.get("error.server.internal"));
    }
}