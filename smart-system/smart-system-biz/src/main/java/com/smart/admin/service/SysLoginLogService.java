package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysLoginLog;

/**
 * Login log service interface.
 */
public interface SysLoginLogService extends IService<SysLoginLog> {

    /**
     * Record login success.
     */
    void recordLoginSuccess(Long userId, String username, Long tenantId, String ip, String userAgent);

    /**
     * Record login failure.
     */
    void recordLoginFailure(String username, Long tenantId, String ip, String userAgent, String msg);

    /**
     * Record logout.
     */
    void recordLogout(Long userId, String username, Long tenantId, String ip, String userAgent);

    /**
     * Record account locked.
     */
    void recordAccountLocked(String username, Long tenantId, String ip, String userAgent);

    /**
     * Clear login logs older than given days, returning affected rows.
     */
    int clearBefore(int days);
}