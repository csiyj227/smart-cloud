package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI conversation message.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
@TenantEntity
public class AiMessageEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    /** USER, ASSISTANT, SYSTEM, TOOL */
    private String role;
    private String content;
    /** Reasoning/thinking content for deep-think models */
    private String reasoningContent;
    /** TEXT, IMAGE, FILE, MIXED */
    private String contentType;
    private Integer tokenCount;
    private String modelCode;
    /** JSON: file attachments info */
    private String attachments;
    /** JSON: function/tool call requests */
    private String toolCalls;
    /** JSON: tool execution results */
    private String toolResult;
    /** JSON: web search results */
    private String searchResults;
    private Long parentId;
    /** SENDING, STREAMING, SUCCESS, ERROR */
    private String status;
    private String errorMsg;
}
