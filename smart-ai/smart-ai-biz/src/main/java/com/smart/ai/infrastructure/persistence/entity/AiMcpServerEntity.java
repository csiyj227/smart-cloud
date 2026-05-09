package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP server configuration.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_mcp_server")
@TenantEntity
public class AiMcpServerEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String serverName;
    /** SSE or STDIO */
    private String transportType;
    private String serverUrl;
    private String command;
    /** JSON: command line arguments */
    private String args;
    /** JSON: environment variables */
    private String envVars;
    private String status;
    private String remark;
}
