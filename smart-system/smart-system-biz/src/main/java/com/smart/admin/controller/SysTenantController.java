package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysTenant;
import com.smart.admin.service.SysTenantService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService sysTenantService;

    @GetMapping("/list")
    public ApiResult<List<SysTenant>> list() {
        return ApiResult.success(sysTenantService.list(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getStatus, "0")
                .orderByAsc(SysTenant::getId)));
    }

    @PreAuthorize("@authz.hasPermission('sys_tenant_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysTenant>> page(Page<SysTenant> page, SysTenant query) {
        return ApiResult.success(sysTenantService.page(page, Wrappers.<SysTenant>lambdaQuery()
                .like(query.getTenantName() != null && !query.getTenantName().isEmpty(), SysTenant::getTenantName, query.getTenantName())
                .like(query.getTenantCode() != null && !query.getTenantCode().isEmpty(), SysTenant::getTenantCode, query.getTenantCode())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysTenant::getStatus, query.getStatus())));
    }

    @PreAuthorize("@authz.hasPermission('sys_tenant_view')")
    @GetMapping("/{id}")
    public ApiResult<SysTenant> getById(@PathVariable Long id) {
        return ApiResult.success(sysTenantService.getById(id));
    }

    @PreAuthorize("@authz.hasPermission('sys_tenant_add')")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysTenant tenant) {
        sysTenantService.save(tenant);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_tenant_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysTenant tenant) {
        sysTenantService.updateById(tenant);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_tenant_del')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        sysTenantService.removeById(id);
        return ApiResult.success();
    }
}