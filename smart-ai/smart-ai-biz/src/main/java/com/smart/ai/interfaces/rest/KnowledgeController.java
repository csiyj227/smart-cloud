package com.smart.ai.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.KnowledgeBaseCmd;
import com.smart.ai.application.KnowledgeService;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeDocumentEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeDocumentMapper;
import com.smart.ai.infrastructure.rag.DocumentParseService;
import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Knowledge base management endpoints.
 */
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
@Tag(name = "AI Knowledge Base")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DocumentParseService documentParseService;
    private final AiKnowledgeDocumentMapper documentMapper;

    // ⚠️ 路径约定：和前端 smart-ui/src/api/ai.ts 保持完全一致：
    //   - 知识库列表/CRUD：/ai/knowledge        （直接用 base path，不要加 /bases）
    //   - 文档分页：/ai/knowledge/{kbId}/documents
    //   - 文档其他操作：/ai/knowledge/documents/...
    //   - 重建索引：/ai/knowledge/bases/{id}/reindex （这里前端确实带了 /bases 前缀）

    @GetMapping("/page")
    @Operation(summary = "Page query knowledge bases")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_view')")
    public ApiResult<?> pageBases(@RequestParam(defaultValue = "1") Integer current,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) String keyword) {
        return ApiResult.success(knowledgeService.page(new Page<>(current, size), keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get knowledge base by ID")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_view')")
    public ApiResult<?> getBaseById(@PathVariable Long id) {
        return ApiResult.success(knowledgeService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create knowledge base")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_add')")
    public ApiResult<?> saveBase(@Valid @RequestBody KnowledgeBaseCmd cmd) {
        return ApiResult.success(knowledgeService.save(cmd));
    }

    @PutMapping
    @Operation(summary = "Update knowledge base")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_edit')")
    public ApiResult<?> updateBase(@Valid @RequestBody KnowledgeBaseCmd cmd) {
        knowledgeService.update(cmd);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete knowledge base")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_del')")
    public ApiResult<?> deleteBase(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResult.success();
    }

    @GetMapping("/{kbId}/documents")
    @Operation(summary = "Page query documents in a knowledge base")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_view')")
    public ApiResult<?> pageDocuments(@PathVariable Long kbId,
                              @RequestParam(defaultValue = "1") Integer current,
                              @RequestParam(defaultValue = "10") Integer size) {
        return ApiResult.success(knowledgeService.pageDocuments(new Page<>(current, size), kbId));
    }

    @PostMapping("/documents")
    @Operation(summary = "Add a document to a knowledge base")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_add')")
    public ApiResult<?> addDocument(@RequestParam Long kbId,
                            @RequestParam String docName,
                            @RequestParam String docType,
                            @RequestParam String fileUrl,
                            @RequestParam(required = false, defaultValue = "0") Long fileSize) {
        AiKnowledgeDocumentEntity document = new AiKnowledgeDocumentEntity();
        document.setKbId(kbId);
        document.setDocName(docName);
        document.setDocType(docType);
        document.setFileUrl(fileUrl);
        document.setFileSize(fileSize);
        document.setParseStatus("PENDING");
        document.setSegmentCount(0);
        document.setTokenCount(0);
        documentMapper.insert(document);
        return ApiResult.success(document.getId());
    }

    @PostMapping("/documents/{id}/parse")
    @Operation(summary = "Trigger document parsing (async)")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_edit')")
    public ApiResult<?> parseDocument(@PathVariable Long id) {
        AiKnowledgeDocumentEntity document = documentMapper.selectById(id);
        if (document == null) {
            return ApiResult.failure("Document not found");
        }
        documentParseService.parseDocumentAsync(id);
        return ApiResult.success("Document parsing started");
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete a document and its segments")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_del')")
    public ApiResult<?> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return ApiResult.success();
    }

    @GetMapping("/segments")
    @Operation(summary = "List segments of a document")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_view')")
    public ApiResult<?> listSegments(@RequestParam Long documentId) {
        return ApiResult.success(knowledgeService.listSegments(documentId));
    }

    @PostMapping("/bases/{id}/reindex")
    @Operation(summary = "Reindex (re-parse + re-embed) all documents in a knowledge base")
    @PreAuthorize("@authz.hasPermission('ai_knowledge_edit')")
    public ApiResult<?> reindexAll(@PathVariable Long id) {
        int queued = knowledgeService.reindexAll(id);
        return ApiResult.success("Queued " + queued + " documents for reindex");
    }
}
