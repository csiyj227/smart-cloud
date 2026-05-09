package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI conversation session.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
@TenantEntity
public class AiConversationEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private Long modelConfigId;
    private Long agentId;
    private Long userId;
    private Integer messageCount;
    private Long totalTokens;
    private Boolean pinned;
}
