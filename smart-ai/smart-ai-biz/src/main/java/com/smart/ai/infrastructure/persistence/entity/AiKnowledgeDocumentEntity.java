package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Knowledge base document.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_document")
@TenantEntity
public class AiKnowledgeDocumentEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;
    private String docName;
    /** PDF, WORD, TXT, MARKDOWN, URL */
    private String docType;
    private String fileUrl;
    private Long fileSize;
    private String content;
    private Integer segmentCount;
    private Integer tokenCount;
    /** PENDING, PARSING, COMPLETED, FAILED */
    private String parseStatus;
    private String errorMsg;
    private Long tenantId;
}
