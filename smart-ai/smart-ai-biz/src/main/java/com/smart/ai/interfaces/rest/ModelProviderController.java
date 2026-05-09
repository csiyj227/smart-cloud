package com.smart.ai.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.ModelProviderCmd;
import com.smart.ai.application.ModelProviderService;
import com.smart.ai.infrastructure.persistence.entity.AiModelProviderEntity;
import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Model provider management endpoints.
 */
@RestController
@RequestMapping("/ai/model-provider")
@RequiredArgsConstructor
@Tag(name = "AI Model Provider")
public class ModelProviderController {

    private final ModelProviderService providerService;

    @GetMapping("/page")
    @Operation(summary = "Page query model providers")
    @PreAuthorize("@authz.hasPermission('ai_model_view')")
    public ApiResult<?> page(@RequestParam(defaultValue = "1") Integer current,
                     @RequestParam(defaultValue = "10") Integer size,
                     @RequestParam(required = false) String keyword) {
        return ApiResult.success(providerService.page(new Page<>(current, size), keyword));
    }

    @GetMapping("/list")
    @Operation(summary = "List all enabled providers")
    @PreAuthorize("@authz.hasPermission('ai_model_view')")
    public ApiResult<?> list() {
        return ApiResult.success(providerService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get provider by ID")
    @PreAuthorize("@authz.hasPermission('ai_model_view')")
    public ApiResult<?> getById(@PathVariable Long id) {
        return ApiResult.success(providerService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create model provider")
    @PreAuthorize("@authz.hasPermission('ai_model_add')")
    public ApiResult<?> save(@Valid @RequestBody ModelProviderCmd cmd) {
        return ApiResult.success(providerService.save(cmd));
    }

    @PutMapping
    @Operation(summary = "Update model provider")
    @PreAuthorize("@authz.hasPermission('ai_model_edit')")
    public ApiResult<?> update(@Valid @RequestBody ModelProviderCmd cmd) {
        providerService.update(cmd);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete model provider")
    @PreAuthorize("@authz.hasPermission('ai_model_del')")
    public ApiResult<?> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ApiResult.success();
    }
}
