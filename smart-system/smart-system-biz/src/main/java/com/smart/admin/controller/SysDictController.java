package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysDict;
import com.smart.admin.entity.SysDictItem;
import com.smart.admin.service.SysDictItemService;
import com.smart.admin.service.SysDictService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.log.annotation.AuditTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService sysDictService;
    private final SysDictItemService sysDictItemService;

    @PreAuthorize("@authz.hasPermission('sys_dict_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysDict>> page(Page<SysDict> page, SysDict query) {
        return ApiResult.success(sysDictService.page(page, Wrappers.<SysDict>lambdaQuery()
                .like(query.getTypeCode() != null && !query.getTypeCode().isEmpty(), SysDict::getTypeCode, query.getTypeCode())
                .like(query.getTypeName() != null && !query.getTypeName().isEmpty(), SysDict::getTypeName, query.getTypeName())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysDict::getStatus, query.getStatus())));
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_view')")
    @GetMapping("/{id}")
    public ApiResult<SysDict> getById(@PathVariable Long id) {
        return ApiResult.success(sysDictService.getById(id));
    }

    @GetMapping("/type/{typeCode}")
    public ApiResult<List<SysDictItem>> getByTypeCode(@PathVariable String typeCode) {
        Long tenantId = TenantContext.get().orElse(null);
        SysDict dict = sysDictService.findByTypeCode(typeCode, tenantId);
        if (dict == null) {
            return ApiResult.success(List.of());
        }
        return ApiResult.success(sysDictItemService.findByDictId(dict.getId()));
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_add')")
    @AuditTrace("新增字典类型")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysDict dict) {
        sysDictService.save(dict);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_edit')")
    @AuditTrace("修改字典类型")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysDict dict) {
        sysDictService.updateById(dict);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_del')")
    @AuditTrace("删除字典类型")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        sysDictService.deleteDictWithItems(id);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_add')")
    @AuditTrace("新增字典项")
    @PostMapping("/item")
    public ApiResult<Void> saveItem(@RequestBody SysDictItem item) {
        sysDictItemService.save(item);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_edit')")
    @AuditTrace("修改字典项")
    @PutMapping("/item")
    public ApiResult<Void> updateItem(@RequestBody SysDictItem item) {
        sysDictItemService.updateById(item);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dict_del')")
    @AuditTrace("删除字典项")
    @DeleteMapping("/item/{id}")
    public ApiResult<Void> deleteItem(@PathVariable Long id) {
        sysDictItemService.removeById(id);
        return ApiResult.success();
    }
}