package com.smart.common.core.exception;

/**
 * Thrown when captcha validation fails.
 *
 * 验证码校验失败时抛出的异常。
 */
public class CaptchaException extends RuntimeException {

    public CaptchaException(String message) {
        super(message);
    }
}