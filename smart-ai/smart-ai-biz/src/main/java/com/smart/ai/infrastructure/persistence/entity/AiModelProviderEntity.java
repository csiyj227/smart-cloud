package com.smart.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI model provider entity (OpenAI / Qwen / DeepSeek / Ollama etc.).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_provider")
@TenantEntity
public class AiModelProviderEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String providerName;
    private String providerType;
    private String baseUrl;
    private String apiKey;
    private String status;
    private Integer sortOrder;
    private String remark;
}
