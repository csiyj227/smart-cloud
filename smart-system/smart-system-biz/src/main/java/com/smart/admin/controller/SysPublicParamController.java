package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysPublicParam;
import com.smart.admin.service.SysPublicParamService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/param")
@RequiredArgsConstructor
public class SysPublicParamController {

    private final SysPublicParamService sysPublicParamService;

    @PreAuthorize("@authz.hasPermission('sys_param_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysPublicParam>> page(Page<SysPublicParam> page, SysPublicParam query) {
        return ApiResult.success(sysPublicParamService.page(page, Wrappers.<SysPublicParam>lambdaQuery()
                .like(query.getParamName() != null && !query.getParamName().isEmpty(), SysPublicParam::getParamName, query.getParamName())
                .like(query.getParamKey() != null && !query.getParamKey().isEmpty(), SysPublicParam::getParamKey, query.getParamKey())
                .eq(query.getParamType() != null && !query.getParamType().isEmpty(), SysPublicParam::getParamType, query.getParamType())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysPublicParam::getStatus, query.getStatus())));
    }

    @PreAuthorize("@authz.hasPermission('sys_param_view')")
    @GetMapping("/{id}")
    public ApiResult<SysPublicParam> getById(@PathVariable Long id) {
        return ApiResult.success(sysPublicParamService.getById(id));
    }

    @PreAuthorize("@authz.hasPermission('sys_param_add')")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysPublicParam param) {
        sysPublicParamService.save(param);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_param_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysPublicParam param) {
        sysPublicParamService.updateById(param);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_param_del')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        sysPublicParamService.removeById(id);
        return ApiResult.success();
    }
}