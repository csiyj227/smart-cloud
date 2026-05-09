package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP tool registration (auto-discovered or manually added).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_mcp_tool")
@TenantEntity
public class AiMcpToolEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serverId;
    private String toolName;
    private String toolDescription;
    /** JSON: input parameter schema */
    private String inputSchema;
    private String status;
}
