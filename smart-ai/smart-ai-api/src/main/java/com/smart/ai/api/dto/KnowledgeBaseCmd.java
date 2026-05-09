package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Command to create or update a knowledge base.
 *
 * 同 ModelProviderCmd：忽略前端回传的审计字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeBaseCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "Knowledge base name is required")
    private String kbName;

    private String description;
    private Long embeddingModelId;
    private BigDecimal similarityThreshold;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String status;
}
