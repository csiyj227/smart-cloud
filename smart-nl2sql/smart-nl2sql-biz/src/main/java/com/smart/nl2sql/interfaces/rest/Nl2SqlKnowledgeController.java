package com.smart.nl2sql.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.core.web.ApiResult;
import com.smart.nl2sql.api.dto.Nl2SqlKnowledgeDTO;
import com.smart.nl2sql.application.Nl2SqlKnowledgeService;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlKnowledgeEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NL2SQL knowledge management endpoints (sql examples, terms, rules, mappings).
 */
@RestController
@RequestMapping("/nl2sql/knowledge")
@RequiredArgsConstructor
@Tag(name = "NL2SQL Knowledge")
public class Nl2SqlKnowledgeController {

    private final Nl2SqlKnowledgeService knowledgeService;

    @GetMapping("/page")
    @Operation(summary = "Page knowledge by dataset and type")
    @PreAuthorize("@authz.hasPermission('nl2sql_knowledge')")
    public ApiResult<Page<Nl2sqlKnowledgeEntity>> page(@RequestParam(defaultValue = "1") Integer current,
                                               @RequestParam(defaultValue = "20") Integer size,
                                               @RequestParam Long datasetId,
                                               @RequestParam(required = false) String type) {
        return ApiResult.success(knowledgeService.page(new Page<>(current, size), datasetId, type));
    }

    @PostMapping
    @Operation(summary = "Create knowledge")
    @PreAuthorize("@authz.hasPermission('nl2sql_knowledge_add')")
    public ApiResult<?> create(@Valid @RequestBody Nl2SqlKnowledgeDTO dto) {
        knowledgeService.create(dto);
        return ApiResult.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update knowledge")
    @PreAuthorize("@authz.hasPermission('nl2sql_knowledge_edit')")
    public ApiResult<?> update(@PathVariable Long id, @Valid @RequestBody Nl2SqlKnowledgeDTO dto) {
        dto.setId(id);
        knowledgeService.update(dto);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete knowledge")
    @PreAuthorize("@authz.hasPermission('nl2sql_knowledge_del')")
    public ApiResult<?> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResult.success();
    }
}
