package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * System operation log entity.
 */
@Data
@TableName("sys_log")
@TenantEntity
public class SysLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

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
    private OffsetDateTime createTime;
}