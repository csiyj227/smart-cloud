package com.smart.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Login/logout event type for audit logging.
 */
@Getter
@AllArgsConstructor
public enum LoginType {

    LOGIN("0", "登录"),
    LOGOUT("1", "登出"),
    REGISTER("2", "注册"),
    PASSWORD_ERROR("3", "密码错误"),
    LOCKED("4", "账号锁定");

    private final String value;
    private final String label;
}