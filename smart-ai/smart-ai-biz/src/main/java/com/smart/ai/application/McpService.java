package com.smart.ai.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.ai.api.dto.McpServerCmd;
import com.smart.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import com.smart.ai.infrastructure.persistence.entity.AiMcpToolEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiMcpServerMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiMcpToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP server and tool management service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {

    private final AiMcpServerMapper serverMapper;
    private final AiMcpToolMapper toolMapper;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public IPage<AiMcpServerEntity> pageServers(Page<AiMcpServerEntity> page, String keyword) {
        return serverMapper.selectPage(page, Wrappers.<AiMcpServerEntity>lambdaQuery()
                .like(keyword != null, AiMcpServerEntity::getServerName, keyword)
                .orderByDesc(AiMcpServerEntity::getCreateTime));
    }

    public AiMcpServerEntity getServerById(Long id) {
        return serverMapper.selectById(id);
    }

    public Long saveServer(McpServerCmd cmd) {
        AiMcpServerEntity entity = new AiMcpServerEntity();
        BeanUtils.copyProperties(cmd, entity);
        serverMapper.insert(entity);
        return entity.getId();
    }

    public void updateServer(McpServerCmd cmd) {
        AiMcpServerEntity entity = new AiMcpServerEntity();
        BeanUtils.copyProperties(cmd, entity);
        serverMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(Long id) {
        toolMapper.delete(Wrappers.<AiMcpToolEntity>lambdaQuery()
                .eq(AiMcpToolEntity::getServerId, id));
        serverMapper.deleteById(id);
    }

    public List<AiMcpToolEntity> listToolsByServer(Long serverId) {
        return toolMapper.selectList(Wrappers.<AiMcpToolEntity>lambdaQuery()
                .eq(AiMcpToolEntity::getServerId, serverId)
                .eq(AiMcpToolEntity::getStatus, "1"));
    }

    public List<AiMcpToolEntity> listAllTools() {
        return toolMapper.selectList(Wrappers.<AiMcpToolEntity>lambdaQuery()
                .eq(AiMcpToolEntity::getStatus, "1"));
    }

    /**
     * 同步指定 MCP server 的工具列表到 {@code ai_mcp_tool} 表。
     *
     * <p>设计要点：
     * <ul>
     *   <li>SSE 模式：通过 JSON-RPC {@code tools/list} 主动拉取工具元数据并 upsert</li>
     *   <li>STDIO 模式：暂未实现（McpToolInvokeService 也未支持），抛异常提示</li>
     *   <li>upsert 策略：以 {@code (server_id, tool_name)} 为唯一键，存在则更新描述和 schema，
     *       不存在则新增。已下线（status='0'）的工具如果 server 不再返回，会保持下线状态不变；
     *       已存在的工具被同步时统一恢复成 status='1'，便于运维误删后一键找回。</li>
     * </ul>
     *
     * @param serverId 要同步的 MCP server ID
     * @return 本次同步成功写入/更新的工具数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncTools(Long serverId) {
        AiMcpServerEntity server = serverMapper.selectById(serverId);
        if (server == null) {
            throw new IllegalArgumentException("MCP server not found: " + serverId);
        }
        if (!"1".equals(server.getStatus())) {
            throw new IllegalStateException("MCP server is disabled: " + server.getServerName());
        }
        if (!"SSE".equalsIgnoreCase(server.getTransportType())) {
            throw new UnsupportedOperationException(
                    "Only SSE transport supports tool auto-discovery. STDIO transport requires manual tool registration.");
        }
        String serverUrl = server.getServerUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalStateException("MCP server URL is not configured: " + server.getServerName());
        }

        List<Map<String, Object>> tools = fetchToolsViaJsonRpc(serverUrl);
        log.info("Fetched {} tools from MCP server {} ({})", tools.size(), server.getServerName(), serverUrl);

        int affected = 0;
        for (Map<String, Object> toolMeta : tools) {
            String toolName = (String) toolMeta.get("name");
            if (toolName == null || toolName.isBlank()) {
                continue;
            }
            String description = (String) toolMeta.get("description");
            // inputSchema 是嵌套 JSON 对象，序列化成字符串存到 text 列
            Object schemaObj = toolMeta.get("inputSchema");
            String inputSchemaJson = null;
            if (schemaObj != null) {
                try {
                    inputSchemaJson = objectMapper.writeValueAsString(schemaObj);
                } catch (Exception e) {
                    log.warn("Failed to serialize inputSchema for tool {}: {}", toolName, e.getMessage());
                }
            }

            // 按 (server_id, tool_name) 查询已有记录
            AiMcpToolEntity existing = toolMapper.selectOne(
                    Wrappers.<AiMcpToolEntity>lambdaQuery()
                            .eq(AiMcpToolEntity::getServerId, serverId)
                            .eq(AiMcpToolEntity::getToolName, toolName)
                            .last("LIMIT 1"));

            if (existing == null) {
                AiMcpToolEntity entity = new AiMcpToolEntity();
                entity.setServerId(serverId);
                entity.setToolName(toolName);
                entity.setToolDescription(description);
                entity.setInputSchema(inputSchemaJson);
                entity.setStatus("1");
                toolMapper.insert(entity);
            } else {
                existing.setToolDescription(description);
                existing.setInputSchema(inputSchemaJson);
                existing.setStatus("1");
                toolMapper.updateById(existing);
            }
            affected++;
        }
        return affected;
    }

    /**
     * 调 MCP server 的 JSON-RPC {@code tools/list} 接口拿工具列表。
     *
     * <p>请求路径策略：MCP 标准的 SSE 服务器通常把 JSON-RPC 收件路径暴露在 {@code /message}（与
     * SSE 推送路径区分），与 {@code McpToolInvokeService#invokeViaSse} 的写法保持一致：
     * {@code POST {serverUrl}/message}。
     *
     * <p>事务安全保证：本方法被 {@link #syncTools(Long)} 在 {@code @Transactional} 内调用，
     * 任何异常都会触发回滚，所以连不上 server 时绝对不会有任何脏数据写到 {@code ai_mcp_tool} 表。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchToolsViaJsonRpc(String serverUrl) {
        String url = serverUrl.endsWith("/") ? serverUrl + "message" : serverUrl + "/message";
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("method", "tools/list");
            requestBody.put("id", UUID.randomUUID().toString());
            requestBody.put("params", Map.of());
            String body = objectMapper.writeValueAsString(requestBody);

            log.info("Probing MCP server tools/list: POST {}", url);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(String.format(
                        "MCP server returned HTTP %d on %s. Response: %s",
                        response.statusCode(), url,
                        response.body() == null || response.body().isBlank() ? "<empty>" : response.body()));
            }

            Map<String, Object> json = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() {});
            // 优先解析 result.tools，兼容个别实现把 tools 直接挂在根的情况
            Object resultObj = json.get("result");
            Map<String, Object> result = resultObj instanceof Map ? (Map<String, Object>) resultObj : json;
            Object toolsObj = result.get("tools");
            if (toolsObj instanceof List) {
                return (List<Map<String, Object>>) toolsObj;
            }
            // 服务端返回了错误码
            Object errorObj = json.get("error");
            if (errorObj != null) {
                throw new IllegalStateException("MCP server returned JSON-RPC error: " + errorObj);
            }
            return List.of();
        } catch (java.net.ConnectException e) {
            // 最常见：server 没启动 / URL 端口写错 / 防火墙拦截
            throw new IllegalStateException(String.format(
                    "无法连接 MCP server: %s。请检查：(1) MCP server 是否已启动；"
                            + "(2) 服务地址和端口是否正确（注意区分 localhost 与公网 IP）；"
                            + "(3) 防火墙/网络是否允许后端访问该地址。底层错误：%s",
                    url, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException(String.format(
                    "MCP server 响应超时（>15s）: %s。请检查 server 是否健康。", url), e);
        } catch (IllegalArgumentException e) {
            // URI.create 对非法 URL 抛 IllegalArgumentException（包装了 URISyntaxException）
            throw new IllegalStateException(String.format(
                    "MCP server URL 格式错误: %s。请确认填写了带协议的完整 URL，如 http://localhost:3000/sse。",
                    serverUrl), e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(String.format(
                    "MCP server 响应不是合法 JSON-RPC 格式（地址：%s）。可能你的 server 不是 MCP 标准协议，"
                            + "或访问路径不对。底层错误：%s", url, e.getOriginalMessage()), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "调用 MCP server (%s) 失败：%s",
                    url, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }
}
