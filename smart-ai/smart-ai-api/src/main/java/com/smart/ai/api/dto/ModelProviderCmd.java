package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Command to create or update a model provider.
 *
 * 前端编辑场景习惯把整个 row（含 createBy/createTime/updateBy/updateTime/delFlag 等审计字段）
 * 原样 PUT 回来。DTO 只关心业务字段，审计字段统一忽略，避免 Jackson 严格模式抛
 * UnrecognizedPropertyException。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelProviderCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "Provider name is required")
    private String providerName;

    @NotBlank(message = "Provider type is required")
    private String providerType;

    private String baseUrl;
    private String apiKey;
    private String status;
    private Integer sortOrder;
    private String remark;
}
