package com.smart.ai.infrastructure.mcp;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import com.smart.ai.infrastructure.persistence.entity.AiMcpToolEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiMcpServerMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiMcpToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * MCP tool invocation service.
 *
 * <p>Converts MCP tools registered in the database into Spring AI {@link ToolCallback}
 * instances, enabling the LLM to call external tools via the Tool Calling mechanism
 * (formerly Function Calling — renamed in Spring AI 1.0 GA).
 *
 * <p>For SSE-based MCP servers, this service acts as an HTTP client that forwards
 * tool invocations to the MCP server's REST endpoint. For STDIO-based servers,
 * it manages the subprocess lifecycle (future enhancement).
 *
 * <p>Usage flow:
 * <ol>
 *   <li>Agent is configured with MCP tools via ai_agent_mcp table</li>
 *   <li>When building the prompt, tools are registered as ToolCallbacks</li>
 *   <li>LLM decides to call a tool → Spring AI invokes the callback</li>
 *   <li>Callback sends the request to the MCP server and returns the result</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolInvokeService {

    private final AiMcpServerMapper serverMapper;
    private final AiMcpToolMapper toolMapper;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Build ToolCallback list for the given MCP tool IDs.
     * These callbacks can be attached to ChatOptions for Tool Calling.
     *
     * @param toolIds list of MCP tool IDs to enable
     * @return list of ToolCallback wrappers
     */
    public List<ToolCallback> buildFunctionCallbacks(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }

        List<ToolCallback> callbacks = new ArrayList<>();

        for (Long toolId : toolIds) {
            AiMcpToolEntity tool = toolMapper.selectById(toolId);
            if (tool == null || !"1".equals(tool.getStatus())) {
                continue;
            }

            AiMcpServerEntity server = serverMapper.selectById(tool.getServerId());
            if (server == null || !"1".equals(server.getStatus())) {
                continue;
            }

            try {
                ToolCallback callback = createCallback(server, tool);
                callbacks.add(callback);
                log.debug("Registered MCP tool as tool callback: {} (server: {})",
                        tool.getToolName(), server.getServerName());
            } catch (Exception e) {
                log.warn("Failed to create ToolCallback for tool {}: {}",
                        tool.getToolName(), e.getMessage());
            }
        }

        return callbacks;
    }

    /**
     * Build ToolCallback list for all enabled tools on a given MCP server.
     *
     * @param serverId the MCP server ID
     * @return list of ToolCallback wrappers
     */
    public List<ToolCallback> buildFunctionCallbacksByServer(Long serverId) {
        List<AiMcpToolEntity> tools = toolMapper.selectList(
                Wrappers.<AiMcpToolEntity>lambdaQuery()
                        .eq(AiMcpToolEntity::getServerId, serverId)
                        .eq(AiMcpToolEntity::getStatus, "1"));

        List<Long> toolIds = tools.stream().map(AiMcpToolEntity::getId).toList();
        return buildFunctionCallbacks(toolIds);
    }

    /**
     * Attach MCP tool callbacks to existing ChatOptions.
     *
     * @param baseOptions the base chat options
     * @param toolCallbacks the tool callbacks to add
     * @return new ChatOptions with tools registered
     */
    public OpenAiChatOptions withFunctions(OpenAiChatOptions baseOptions,
                                            List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return baseOptions;
        }

        return OpenAiChatOptions.builder()
                .model(baseOptions.getModel())
                .maxTokens(baseOptions.getMaxTokens())
                .temperature(baseOptions.getTemperature())
                .topP(baseOptions.getTopP())
                // Spring AI 1.0 GA: functionCallbacks() → toolCallbacks()
                .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .build();
    }

    /**
     * Create a ToolCallback that invokes the MCP tool via HTTP (SSE transport)
     * or subprocess (STDIO transport).
     *
     * <p>Uses {@link FunctionToolCallback} (Spring AI 1.0 GA replacement for
     * {@code FunctionCallback.builder().function(...)}).
     */
    private ToolCallback createCallback(AiMcpServerEntity server, AiMcpToolEntity tool) {
        String description = tool.getToolDescription() != null
                ? tool.getToolDescription()
                : "MCP tool: " + tool.getToolName();

        return FunctionToolCallback
                .builder(tool.getToolName(),
                        (String jsonInput) -> invokeTool(server, tool, jsonInput))
                .description(description)
                .inputType(String.class)
                .build();
    }

    /**
     * Invoke an MCP tool. Routes to SSE or STDIO based on transport type.
     */
    private String invokeTool(AiMcpServerEntity server, AiMcpToolEntity tool, String jsonInput) {
        log.info("Invoking MCP tool: {} on server: {} with input: {}",
                tool.getToolName(), server.getServerName(), jsonInput);

        return switch (server.getTransportType()) {
            case "SSE" -> invokeViaSse(server, tool, jsonInput);
            case "STDIO" -> invokeViaStdio(server, tool, jsonInput);
            default -> "Unsupported transport type: " + server.getTransportType();
        };
    }

    /**
     * Invoke MCP tool via SSE (HTTP POST to the server's tool endpoint).
     *
     * <p>Sends a JSON-RPC style request to the MCP server:
     * POST {serverUrl}/tools/{toolName}/invoke
     */
    private String invokeViaSse(AiMcpServerEntity server, AiMcpToolEntity tool, String jsonInput) {
        try {
            String serverUrl = server.getServerUrl();
            if (serverUrl == null || serverUrl.isBlank()) {
                return "Error: MCP server URL is not configured";
            }

            // Build JSON-RPC request body
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("method", "tools/call");
            requestBody.put("id", UUID.randomUUID().toString());

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", tool.getToolName());

            // Parse input arguments
            if (jsonInput != null && !jsonInput.isBlank()) {
                try {
                    Map<String, Object> arguments = objectMapper.readValue(
                            jsonInput, new TypeReference<Map<String, Object>>() {});
                    params.put("arguments", arguments);
                } catch (Exception e) {
                    params.put("arguments", Map.of("input", jsonInput));
                }
            }

            requestBody.put("params", params);
            String body = objectMapper.writeValueAsString(requestBody);

            // Normalize URL
            String url = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
            url = url + "message";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("MCP tool {} invoked successfully, status: {}", tool.getToolName(), response.statusCode());
                return response.body();
            } else {
                String errorMsg = String.format("MCP tool invocation failed: HTTP %d - %s",
                        response.statusCode(), response.body());
                log.error(errorMsg);
                return errorMsg;
            }
        } catch (Exception e) {
            log.error("Failed to invoke MCP tool {} via SSE: {}", tool.getToolName(), e.getMessage(), e);
            return "Error invoking MCP tool: " + e.getMessage();
        }
    }

    /**
     * Invoke MCP tool via STDIO (subprocess).
     * This is a placeholder for future implementation.
     */
    private String invokeViaStdio(AiMcpServerEntity server, AiMcpToolEntity tool, String jsonInput) {
        log.warn("STDIO transport for MCP is not yet implemented. Server: {}, Tool: {}",
                server.getServerName(), tool.getToolName());
        return "STDIO transport is not yet supported. Please use SSE transport instead. "
                + "Configure your MCP server with transport_type=SSE and provide a server_url.";
    }
}
