package com.smart.admin.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Operation log DTO for Feign-based async log persistence.
 *
 * 操作日志 DTO，用于基于 Feign 的异步日志持久化。
 */
@Data
public class SysLogDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String logType;
    private String title;
    private String serviceId;
    private String remoteAddr;
    private String userAgent;
    private String requestUri;
    private String httpMethod;
    private String className;
    private String methodName;
    private String params;
    private Long executionTime;
    private String exception;
    private String traceId;
    private Long tenantId;
    private String createBy;
}