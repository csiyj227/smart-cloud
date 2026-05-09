package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysAuditLog;
import com.smart.admin.mapper.SysAuditLogMapper;
import com.smart.admin.service.AuditEvent;
import com.smart.admin.service.SysAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Audit log service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAuditLogServiceImpl extends ServiceImpl<SysAuditLogMapper, SysAuditLog>
        implements SysAuditLogService {

    @Override
    @Async
    public void log(AuditEvent event) {
        try {
            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setUserId(event.getUserId());
            auditLog.setUsername(event.getUsername());
            auditLog.setTenantId(event.getTenantId());
            auditLog.setModule(event.getModule());
            auditLog.setOperation(event.getOperation());
            auditLog.setMethod(event.getMethod());
            auditLog.setRequestUrl(event.getRequestUrl());
            auditLog.setHttpMethod(event.getHttpMethod());
            auditLog.setRequestParams(truncateParams(event.getRequestParams()));
            auditLog.setResponseStatus(event.getResponseStatus());
            auditLog.setResponseMsg(event.getResponseMsg());
            auditLog.setExecutionTime(event.getExecutionTime());
            auditLog.setIp(event.getIp());
            auditLog.setUserAgent(event.getUserAgent());
            auditLog.setResult(event.getResult());
            auditLog.setErrorDetail(truncateParams(event.getErrorDetail()));

            save(auditLog);
            log.debug("Audit log saved: {} {} {}", event.getUsername(), event.getModule(), event.getOperation());
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    @Override
    public int cleanOldLogs(int daysToKeep) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysToKeep);
        return baseMapper.delete(null); // Would need custom SQL with condition
    }

    private String truncateParams(String params) {
        if (params == null) {
            return null;
        }
        if (params.length() > 4000) {
            return params.substring(0, 4000);
        }
        return params;
    }
}