package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * AI agent definition.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent")
@TenantEntity
public class AiAgentEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentName;
    private String avatar;
    private String description;
    private String systemPrompt;
    private String welcomeMessage;
    private Long modelConfigId;
    private BigDecimal temperatureOverride;
    private Boolean enableWebSearch;
    private Boolean enableDeepThinking;
    private Boolean isPublic;
    private String category;
    private Integer sortOrder;
    private String status;
}
