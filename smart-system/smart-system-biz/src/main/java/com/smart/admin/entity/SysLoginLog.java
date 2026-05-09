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
 * System login log entity.
 * Records login/logout events.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
@TenantEntity
public class SysLoginLog extends AuditableEntity {

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
     * Login type: 0=login, 1=logout, 2=register, 3=password error, 4=account locked
     */
    private String loginType;

    /**
     * Login status: 0=success, 1=failed
     */
    private String status;

    /**
     * IP address
     */
    private String ip;

    /**
     * Login location
     */
    private String location;

    /**
     * User agent / browser info
     */
    private String userAgent;

    /**
     * Error message (if failed)
     */
    private String msg;

    /**
     * Access token (partial, for tracking)
     */
    private String accessToken;
}