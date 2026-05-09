package com.smart.admin.controller;

import com.smart.admin.entity.SysDept;
import com.smart.admin.service.SysDeptService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.log.annotation.AuditTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @PreAuthorize("@authz.hasPermission('sys_dept_view')")
    @GetMapping("/tree")
    public ApiResult<List<SysDept>> tree() {
        Long tenantId = TenantContext.get().orElse(null);
        return ApiResult.success(sysDeptService.buildDeptTree(tenantId));
    }

    @PreAuthorize("@authz.hasPermission('sys_dept_view')")
    @GetMapping("/{deptId}")
    public ApiResult<SysDept> getById(@PathVariable Long deptId) {
        return ApiResult.success(sysDeptService.getById(deptId));
    }

    @PreAuthorize("@authz.hasPermission('sys_dept_add')")
    @AuditTrace("新增部门")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysDept dept) {
        sysDeptService.save(dept);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dept_edit')")
    @AuditTrace("修改部门")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysDept dept) {
        sysDeptService.updateById(dept);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_dept_del')")
    @AuditTrace("删除部门")
    @DeleteMapping("/{deptId}")
    public ApiResult<Void> delete(@PathVariable Long deptId) {
        sysDeptService.removeById(deptId);
        return ApiResult.success();
    }
}