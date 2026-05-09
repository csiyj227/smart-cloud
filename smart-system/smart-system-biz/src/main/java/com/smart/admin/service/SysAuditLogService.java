package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysAuditLog;

/**
 * Audit log service interface.
 */
public interface SysAuditLogService extends IService<SysAuditLog> {

    /**
     * Log an audit event.
     */
    void log(AuditEvent event);

    /**
     * Clean old audit logs.
     *
     * @param daysToKeep days to keep
     * @return deleted count
     */
    int cleanOldLogs(int daysToKeep);
}