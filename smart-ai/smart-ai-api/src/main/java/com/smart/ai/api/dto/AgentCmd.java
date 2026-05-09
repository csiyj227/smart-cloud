package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Command to create or update an AI agent.
 *
 * 同 ModelProviderCmd：忽略前端回传的审计字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "Agent name is required")
    private String agentName;

    private String avatar;
    private String description;
    private String systemPrompt;
    private String welcomeMessage;
    private Long modelConfigId;
    private BigDecimal temperatureOverride;
    private Boolean enableWebSearch;
    private Boolean enableDeepThinking;
    private Boolean isPublic;
    private String category;
    private Integer sortOrder;
    private String status;

    /** MCP tool IDs to bind */
    private List<Long> toolIds;

    /** Knowledge base IDs to bind */
    private List<Long> knowledgeBaseIds;
}
