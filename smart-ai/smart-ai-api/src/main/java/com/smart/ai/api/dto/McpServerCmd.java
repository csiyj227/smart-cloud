package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Command to create or update an MCP server.
 *
 * 同 ModelProviderCmd：忽略前端回传的审计字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpServerCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "Server name is required")
    private String serverName;

    @NotBlank(message = "Transport type is required")
    private String transportType;

    private String serverUrl;
    private String command;
    private String args;
    private String envVars;
    private String status;
    private String remark;
}
