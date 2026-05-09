package com.smart.common.core.exception;

import lombok.Getter;

/**
 * Exception representing a business-rule violation.
 *
 * <p>Carries a machine-readable {@code errorCode} (e.g. "USER_NOT_FOUND")
 * that maps directly to {@link com.smart.common.core.web.ApiResult#getErrorCode()}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BIZ_ERROR";
    }

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
