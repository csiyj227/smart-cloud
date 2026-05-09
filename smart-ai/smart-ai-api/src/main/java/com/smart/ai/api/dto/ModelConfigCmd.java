package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Command to create or update a model configuration.
 *
 * 同 ModelProviderCmd：忽略前端回传的审计字段（createBy/createTime/...）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelConfigCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotBlank(message = "Model name is required")
    private String modelName;

    @NotBlank(message = "Model code is required")
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
