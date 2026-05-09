package com.smart.nl2sql.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.core.web.ApiResult;
import com.smart.nl2sql.api.dto.DataSourceDTO;
import com.smart.nl2sql.api.dto.DataSourceTestCmd;
import com.smart.nl2sql.application.DataSourceService;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
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
 * Datasource management for NL2SQL platform.
 */
@RestController
@RequestMapping("/nl2sql/datasource")
@RequiredArgsConstructor
@Tag(name = "NL2SQL DataSource")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @GetMapping("/page")
    @Operation(summary = "Page datasources")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource')")
    public ApiResult<Page<Nl2sqlDatasourceEntity>> page(@RequestParam(defaultValue = "1") Integer current,
                                                @RequestParam(defaultValue = "20") Integer size,
                                                @RequestParam(required = false) String keyword) {
        return ApiResult.success(dataSourceService.page(new Page<>(current, size), keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get datasource detail")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource')")
    public ApiResult<Nl2sqlDatasourceEntity> getById(@PathVariable Long id) {
        return ApiResult.success(dataSourceService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create datasource")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource_add')")
    public ApiResult<?> create(@Valid @RequestBody DataSourceDTO dto) {
        dataSourceService.create(dto);
        return ApiResult.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update datasource")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource_edit')")
    public ApiResult<?> update(@PathVariable Long id, @Valid @RequestBody DataSourceDTO dto) {
        dto.setId(id);
        dataSourceService.update(dto);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete datasource")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource_del')")
    public ApiResult<?> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ApiResult.success();
    }

    @PostMapping("/test")
    @Operation(summary = "Test connection by config")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource')")
    public ApiResult<Boolean> test(@Valid @RequestBody DataSourceTestCmd cmd) {
        return ApiResult.success(dataSourceService.testConnection(cmd));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test connection of an existing datasource")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource')")
    public ApiResult<Boolean> testById(@PathVariable Long id) {
        return ApiResult.success(dataSourceService.testConnectionById(id));
    }

    @GetMapping("/{id}/tables")
    @Operation(summary = "List tables of a datasource")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource')")
    public ApiResult<?> tables(@PathVariable Long id) {
        return ApiResult.success(dataSourceService.getTables(id));
    }

    @GetMapping("/{id}/tables/{tableName}/columns")
    @Operation(summary = "Get columns of a table")
    @PreAuthorize("@authz.hasPermission('nl2sql_datasource')")
    public ApiResult<?> columns(@PathVariable Long id, @PathVariable String tableName) {
        return ApiResult.success(dataSourceService.getTableColumns(id, tableName));
    }
}
