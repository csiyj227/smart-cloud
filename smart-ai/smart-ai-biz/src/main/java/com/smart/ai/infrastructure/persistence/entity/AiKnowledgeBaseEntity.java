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
 * RAG knowledge base.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_base")
@TenantEntity
public class AiKnowledgeBaseEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String kbName;
    private String description;
    /**
     * 关联到 ai_model_config.id —— 决定使用哪个 embedding 模型生成向量。
     * 为空时走 fallback：纯关键词检索，不写入 embedding 字段。
     */
    private Long embeddingModelId;
    private BigDecimal similarityThreshold;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer documentCount;
    private Integer segmentCount;
    private String status;
}
