package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * System audit log entity.
 * Records detailed operations for security auditing.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_audit_log")
@TenantEntity
public class SysAuditLog extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Username
     */
    private String username;

    /**
     * Tenant ID
     */
    private Long tenantId;

    /**
     * Operation module
     */
    private String module;

    /**
     * Operation type: CREATE, UPDATE, DELETE, QUERY, EXPORT, IMPORT, LOGIN, LOGOUT, etc.
     */
    private String operation;

    /**
     * HTTP method
     */
    private String method;

    /**
     * Request URL
     */
    private String requestUrl;

    /**
     * Request method (GET, POST, etc.)
     */
    private String httpMethod;

    /**
     * Request parameters (can be encrypted for sensitive data)
     */
    private String requestParams;

    /**
     * Response status code
     */
    private Integer responseStatus;

    /**
     * Response message
     */
    private String responseMsg;

    /**
     * Execution time in milliseconds
     */
    private Long executionTime;

    /**
     * Client IP address
     */
    private String ip;

    /**
     * User agent
     */
    private String userAgent;

    /**
     * Operation result: SUCCESS, FAIL
     */
    private String result;

    /**
     * Error details (if failed)
     */
    private String errorDetail;
}