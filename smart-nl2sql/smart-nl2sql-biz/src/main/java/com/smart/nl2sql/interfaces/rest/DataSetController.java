package com.smart.nl2sql.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.core.web.ApiResult;
import com.smart.nl2sql.api.dto.ColumnAiEvaluateCmd;
import com.smart.nl2sql.api.dto.ColumnAiSuggestionVO;
import com.smart.nl2sql.api.dto.DataSetColumnDTO;
import com.smart.nl2sql.api.dto.DataSetDTO;
import com.smart.nl2sql.api.dto.DataSetRelationDTO;
import com.smart.nl2sql.api.dto.DataSetTableDTO;
import com.smart.nl2sql.application.ColumnAiEvaluator;
import com.smart.nl2sql.application.DataSetService;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetEntity;
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

import java.util.List;

/**
 * Dataset management for NL2SQL platform.
 */
@RestController
@RequestMapping("/nl2sql/dataset")
@RequiredArgsConstructor
@Tag(name = "NL2SQL DataSet")
public class DataSetController {

    private final DataSetService dataSetService;
    private final ColumnAiEvaluator columnAiEvaluator;

    @GetMapping("/page")
    @Operation(summary = "Page datasets")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset')")
    public ApiResult<Page<Nl2sqlDatasetEntity>> page(@RequestParam(defaultValue = "1") Integer current,
                                             @RequestParam(defaultValue = "20") Integer size,
                                             @RequestParam(required = false) String keyword) {
        return ApiResult.success(dataSetService.page(new Page<>(current, size), keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get dataset detail (with tables/columns/relations)")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset')")
    public ApiResult<DataSetDTO> detail(@PathVariable Long id) {
        return ApiResult.success(dataSetService.getDetail(id));
    }

    @PostMapping
    @Operation(summary = "Create dataset")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_add')")
    public ApiResult<Long> create(@Valid @RequestBody DataSetDTO dto) {
        return ApiResult.success(dataSetService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update dataset basic info")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<?> update(@PathVariable Long id, @Valid @RequestBody DataSetDTO dto) {
        dto.setId(id);
        dataSetService.update(dto);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete dataset")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_del')")
    public ApiResult<?> delete(@PathVariable Long id) {
        dataSetService.delete(id);
        return ApiResult.success();
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sync table structure from datasource")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<?> sync(@PathVariable Long id) {
        dataSetService.syncTables(id);
        return ApiResult.success();
    }

    @PutMapping("/{id}/tables")
    @Operation(summary = "Replace tables of dataset")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<?> updateTables(@PathVariable Long id, @RequestBody List<DataSetTableDTO> tables) {
        dataSetService.updateTables(id, tables);
        return ApiResult.success();
    }

    @PutMapping("/{id}/columns")
    @Operation(summary = "Batch update column metadata (remarks / dim / measure)")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<?> updateColumns(@PathVariable Long id, @RequestBody List<DataSetColumnDTO> columns) {
        dataSetService.updateColumns(id, columns);
        return ApiResult.success();
    }

    @PutMapping("/{id}/relations")
    @Operation(summary = "Replace table relations of dataset")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<?> updateRelations(@PathVariable Long id, @RequestBody List<DataSetRelationDTO> relations) {
        dataSetService.updateRelations(id, relations);
        return ApiResult.success();
    }

    /**
     * AI 评估字段含义。仅返回建议，不入库；用户在 UI 上「采纳」后由前端走
     * {@link #updateColumns(Long, List)} 真正落库，让用户保留最终决定权。
     */
    @PostMapping("/{id}/columns/ai-evaluate")
    @Operation(summary = "AI 评估字段含义（返回建议，不直接落库）")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<List<ColumnAiSuggestionVO>> aiEvaluateColumns(@PathVariable Long id,
                                                           @RequestBody(required = false) ColumnAiEvaluateCmd cmd) {
        return ApiResult.success(columnAiEvaluator.evaluate(id, cmd));
    }

    /**
     * 触发数据集学习。当前实现是同步学习（轻量版：完备性校验 + AI 补全空备注 + 状态机更新）。
     * 后续接入向量库后会变成异步任务，前端通过 learn_status 轮询。
     *
     * @param id              数据集 id
     * @param autoFillRemark  是否让 AI 自动补全所有「user_remark 为空」的字段并直接落库；
     *                        默认 true（推荐打开，能大幅提升 NL2SQL 准确率）
     */
    @PostMapping("/{id}/learn")
    @Operation(summary = "触发数据集学习（校验完备性 + 可选 AI 自动补全空备注）")
    @PreAuthorize("@authz.hasPermission('nl2sql_dataset_edit')")
    public ApiResult<?> learn(@PathVariable Long id,
                      @RequestParam(defaultValue = "true") Boolean autoFillRemark) {
        dataSetService.learn(id, Boolean.TRUE.equals(autoFillRemark));
        return ApiResult.success();
    }
}
