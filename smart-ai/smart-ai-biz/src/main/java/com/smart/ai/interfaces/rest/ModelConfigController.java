package com.smart.ai.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.ModelConfigCmd;
import com.smart.ai.application.ModelConfigService;
import com.smart.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Model configuration management endpoints.
 */
@RestController
@RequestMapping("/ai/model-config")
@RequiredArgsConstructor
@Tag(name = "AI Model Configuration")
public class ModelConfigController {

    private final ModelConfigService configService;

    @GetMapping("/page")
    @Operation(summary = "Page query model configs")
    @PreAuthorize("@authz.hasPermission('ai_model_view')")
    public ApiResult<?> page(@RequestParam(defaultValue = "1") Integer current,
                     @RequestParam(defaultValue = "10") Integer size,
                     @RequestParam(required = false) Long providerId) {
        return ApiResult.success(configService.page(new Page<>(current, size), providerId));
    }

    @GetMapping("/list")
    @Operation(summary = "List all enabled model configs")
    @PreAuthorize("@authz.hasPermission('ai_model_view')")
    public ApiResult<?> list(@RequestParam(required = false) Long providerId) {
        return providerId != null
                ? ApiResult.success(configService.listByProvider(providerId))
                : ApiResult.success(configService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get model config by ID")
    @PreAuthorize("@authz.hasPermission('ai_model_view')")
    public ApiResult<?> getById(@PathVariable Long id) {
        return ApiResult.success(configService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create model config")
    @PreAuthorize("@authz.hasPermission('ai_model_add')")
    public ApiResult<?> save(@Valid @RequestBody ModelConfigCmd cmd) {
        return ApiResult.success(configService.save(cmd));
    }

    @PutMapping
    @Operation(summary = "Update model config")
    @PreAuthorize("@authz.hasPermission('ai_model_edit')")
    public ApiResult<?> update(@Valid @RequestBody ModelConfigCmd cmd) {
        configService.update(cmd);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete model config")
    @PreAuthorize("@authz.hasPermission('ai_model_del')")
    public ApiResult<?> delete(@PathVariable Long id) {
        configService.delete(id);
        return ApiResult.success();
    }
}
