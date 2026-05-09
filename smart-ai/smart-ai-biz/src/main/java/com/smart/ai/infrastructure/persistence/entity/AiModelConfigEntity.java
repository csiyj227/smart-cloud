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
 * AI model configuration with specific parameters.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
@TenantEntity
public class AiModelConfigEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;
    private String modelName;
    private String modelCode;
    private Integer maxTokens;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer contextWindow;
    private Boolean supportVision;
    private Boolean supportFunctionCall;
    private String status;
    private Boolean isDefault;
    private String remark;
}
