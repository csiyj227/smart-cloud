package com.smart.common.core.event;

/**
 * 登录事件类型。
 */
public enum LoginEventType {

    /**
     * 登录成功
     */
    LOGIN_SUCCESS,

    /**
     * 登录失败（用户名 / 密码 / 验证码错误等）
     */
    LOGIN_FAILURE,

    /**
     * 账号锁定
     */
    ACCOUNT_LOCKED,

    /**
     * 登出
     */
    LOGOUT
}
