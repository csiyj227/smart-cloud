package com.smart.admin.service;

import lombok.Builder;
import lombok.Data;

/**
 * Audit event data.
 */
@Data
@Builder
public class AuditEvent {

    private Long userId;
    private String username;
    private Long tenantId;
    private String module;
    private String operation;
    private String method;
    private String requestUrl;
    private String httpMethod;
    private String requestParams;
    private Integer responseStatus;
    private String responseMsg;
    private Long executionTime;
    private String ip;
    private String userAgent;
    private String result;
    private String errorDetail;
}