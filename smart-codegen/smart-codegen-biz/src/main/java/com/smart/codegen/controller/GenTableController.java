package com.smart.codegen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.codegen.entity.GenTable;
import com.smart.codegen.service.CodeGeneratorService;
import com.smart.codegen.service.GenTableService;
import com.smart.common.core.web.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 代码生成相关接口。包括：
 * <ul>
 *   <li>{@code GET  /gen/table/page} 分页元数据</li>
 *   <li>{@code POST /gen/table/generate/{id}} 在线预览，返回 {filePath -> content}</li>
 *   <li>{@code GET  /gen/table/download/{id}} 一键下载 zip</li>
 *   <li>{@code GET  /gen/table/db-tables} 列出数据库里未导入的物理表</li>
 *   <li>{@code POST /gen/table/import} 从数据库批量导入表元数据</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/codegen/table")
@RequiredArgsConstructor
public class GenTableController {

    private final GenTableService genTableService;
    private final CodeGeneratorService codeGeneratorService;

    @GetMapping("/page")
    public ApiResult<Page<GenTable>> page(Page<GenTable> page) {
        return ApiResult.success(genTableService.page(page));
    }

    @GetMapping("/{id}")
    public ApiResult<GenTable> getById(@PathVariable Long id) {
        return ApiResult.success(genTableService.getById(id));
    }

    @PostMapping
    public ApiResult<Void> save(@RequestBody GenTable genTable) {
        genTableService.save(genTable);
        return ApiResult.success();
    }

    @PutMapping
    public ApiResult<Void> update(@RequestBody GenTable genTable) {
        genTableService.updateById(genTable);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        genTableService.removeById(id);
        return ApiResult.success();
    }

    /**
     * 在线预览生成结果（不写文件，直接返回 Map），便于前端 diff 或预览。
     */
    @PostMapping("/generate/{id}")
    public ApiResult<Map<String, String>> generate(@PathVariable Long id) {
        return ApiResult.success(codeGeneratorService.generateCode(id));
    }

    /**
     * 下载生成结果 zip。Content-Disposition 用 RFC 5987 编码，避免中文文件名乱码。
     */
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        GenTable table = genTableService.getById(id);
        if (table == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "GenTable not found: " + id);
            return;
        }
        byte[] zip = codeGeneratorService.generateZip(id);
        String filename = (table.getClassName() != null ? table.getClassName() : "code") + "_" + id + ".zip";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        response.setContentLength(zip.length);
        response.getOutputStream().write(zip);
        response.getOutputStream().flush();
    }

    /**
     * 列出数据库里"还没导入"的物理表，用于"从数据库导入"功能的下拉选择。
     */
    @GetMapping("/db-tables")
    public ApiResult<List<Map<String, Object>>> listDatabaseTables(@RequestParam(required = false) String keyword) {
        return ApiResult.success(codeGeneratorService.listDatabaseTables(keyword));
    }

    /**
     * 批量从数据库导入表元数据。返回新建的 GenTable id 列表。
     */
    @PostMapping("/import")
    public ApiResult<List<Long>> importFromDatabase(@RequestBody List<String> tableNames) {
        return ApiResult.success(codeGeneratorService.importFromDatabase(tableNames));
    }
}