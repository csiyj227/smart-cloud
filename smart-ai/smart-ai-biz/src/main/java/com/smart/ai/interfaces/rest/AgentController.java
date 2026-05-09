package com.smart.ai.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.AgentCmd;
import com.smart.ai.application.AgentService;
import com.smart.ai.infrastructure.persistence.entity.AiAgentEntity;
import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent management endpoints.
 */
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
@Tag(name = "AI Agent")
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/page")
    @Operation(summary = "Page query agents")
    @PreAuthorize("@authz.hasPermission('ai_agent_view')")
    public ApiResult<?> page(@RequestParam(defaultValue = "1") Integer current,
                     @RequestParam(defaultValue = "10") Integer size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String category) {
        return ApiResult.success(agentService.page(new Page<>(current, size), keyword, category));
    }

    @GetMapping("/public")
    @Operation(summary = "List all public agents")
    @PreAuthorize("@authz.hasPermission('ai_agent_view')")
    public ApiResult<?> listPublic() {
        return ApiResult.success(agentService.listPublic());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get agent by ID")
    @PreAuthorize("@authz.hasPermission('ai_agent_view')")
    public ApiResult<?> getById(@PathVariable Long id) {
        return ApiResult.success(agentService.getById(id));
    }

    @GetMapping("/{id}/tools")
    @Operation(summary = "Get tool IDs bound to agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_view')")
    public ApiResult<?> getToolIds(@PathVariable Long id) {
        return ApiResult.success(agentService.getToolIds(id));
    }

    @GetMapping("/{id}/knowledge-bases")
    @Operation(summary = "Get knowledge base IDs bound to agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_view')")
    public ApiResult<?> getKnowledgeBaseIds(@PathVariable Long id) {
        return ApiResult.success(agentService.getKnowledgeBaseIds(id));
    }

    @PostMapping
    @Operation(summary = "Create agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_add')")
    public ApiResult<?> save(@Valid @RequestBody AgentCmd cmd) {
        return ApiResult.success(agentService.save(cmd));
    }

    @PutMapping
    @Operation(summary = "Update agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_edit')")
    public ApiResult<?> update(@Valid @RequestBody AgentCmd cmd) {
        agentService.update(cmd);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_del')")
    public ApiResult<?> delete(@PathVariable Long id) {
        agentService.delete(id);
        return ApiResult.success();
    }

    @PutMapping("/{id}/tools")
    @Operation(summary = "Rebind MCP tools for an agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_edit')")
    public ApiResult<?> bindTools(@PathVariable Long id, @RequestBody List<Long> toolIds) {
        agentService.rebindTools(id, toolIds);
        return ApiResult.success();
    }

    @PutMapping("/{id}/knowledge-bases")
    @Operation(summary = "Rebind knowledge bases for an agent")
    @PreAuthorize("@authz.hasPermission('ai_agent_edit')")
    public ApiResult<?> bindKnowledgeBases(@PathVariable Long id, @RequestBody List<Long> kbIds) {
        agentService.rebindKnowledgeBases(id, kbIds);
        return ApiResult.success();
    }
}
