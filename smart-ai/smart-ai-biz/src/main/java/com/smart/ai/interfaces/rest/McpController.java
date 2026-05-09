package com.smart.ai.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.McpServerCmd;
import com.smart.ai.application.McpService;
import com.smart.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import com.smart.ai.infrastructure.persistence.entity.AiMcpToolEntity;
import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP server and tool management endpoints.
 */
@RestController
@RequestMapping("/ai/mcp")
@RequiredArgsConstructor
@Tag(name = "AI MCP")
public class McpController {

    private final McpService mcpService;

    // ⚠️ 路径约定：和前端 smart-ui/src/api/ai.ts 保持完全一致：
    //   - 服务器：/ai/mcp/server/...   （单数 server）
    //   - 工具：  /ai/mcp/tool/...      （单数 tool）
    //   - 服务器下属工具：/ai/mcp/server/{serverId}/tools

    @GetMapping("/server/page")
    @Operation(summary = "Page query MCP servers")
    @PreAuthorize("@authz.hasPermission('ai_mcp_view')")
    public ApiResult<?> pageServers(@RequestParam(defaultValue = "1") Integer current,
                            @RequestParam(defaultValue = "10") Integer size,
                            @RequestParam(required = false) String keyword) {
        return ApiResult.success(mcpService.pageServers(new Page<>(current, size), keyword));
    }

    @GetMapping("/server/{id}")
    @Operation(summary = "Get MCP server by ID")
    @PreAuthorize("@authz.hasPermission('ai_mcp_view')")
    public ApiResult<?> getServerById(@PathVariable Long id) {
        return ApiResult.success(mcpService.getServerById(id));
    }

    @PostMapping("/server")
    @Operation(summary = "Create MCP server")
    @PreAuthorize("@authz.hasPermission('ai_mcp_add')")
    public ApiResult<?> saveServer(@Valid @RequestBody McpServerCmd cmd) {
        return ApiResult.success(mcpService.saveServer(cmd));
    }

    @PutMapping("/server")
    @Operation(summary = "Update MCP server")
    @PreAuthorize("@authz.hasPermission('ai_mcp_edit')")
    public ApiResult<?> updateServer(@Valid @RequestBody McpServerCmd cmd) {
        mcpService.updateServer(cmd);
        return ApiResult.success();
    }

    @DeleteMapping("/server/{id}")
    @Operation(summary = "Delete MCP server")
    @PreAuthorize("@authz.hasPermission('ai_mcp_del')")
    public ApiResult<?> deleteServer(@PathVariable Long id) {
        mcpService.deleteServer(id);
        return ApiResult.success();
    }

    @GetMapping("/server/{serverId}/tools")
    @Operation(summary = "List tools by server ID")
    @PreAuthorize("@authz.hasPermission('ai_mcp_view')")
    public ApiResult<?> listToolsByServer(@PathVariable Long serverId) {
        return ApiResult.success(mcpService.listToolsByServer(serverId));
    }

    @GetMapping("/tool/list")
    @Operation(summary = "List all enabled MCP tools (for agent binding)")
    @PreAuthorize("@authz.hasPermission('ai_mcp_view')")
    public ApiResult<?> listAllTools() {
        return ApiResult.success(mcpService.listAllTools());
    }

    /**
     * 同步指定 MCP server 的工具列表（调 server 的 JSON-RPC tools/list 并 upsert 到 ai_mcp_tool 表）。
     *
     * <p>这是 MCP 工具能在 Agent 编辑下拉里出现的前提：新增 server 后必须执行一次同步，否则
     * ai_mcp_tool 表始终为空。
     */
    @PostMapping("/server/{serverId}/sync-tools")
    @Operation(summary = "Sync tools from MCP server into ai_mcp_tool table")
    @PreAuthorize("@authz.hasPermission('ai_mcp_edit')")
    public ApiResult<?> syncTools(@PathVariable Long serverId) {
        int count = mcpService.syncTools(serverId);
        return ApiResult.success(count);
    }
}
