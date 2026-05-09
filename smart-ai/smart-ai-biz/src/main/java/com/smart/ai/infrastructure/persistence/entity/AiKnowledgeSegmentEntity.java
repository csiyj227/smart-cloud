package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Knowledge segment with vector embedding for RAG retrieval.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_segment")
@TenantEntity
public class AiKnowledgeSegmentEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;
    private Long documentId;
    private Integer segmentIndex;
    private String content;
    private Integer tokenCount;
    /** pgvector stores the embedding; MyBatis reads/writes as String (serialized float[]) */
    private String embedding;
    private String status;
}
