package com.smart.admin.controller;

import com.smart.admin.entity.SysRouteConf;
import com.smart.admin.service.SysRouteConfService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/route")
@RequiredArgsConstructor
public class SysRouteConfController {

    private final SysRouteConfService sysRouteConfService;

    @PreAuthorize("@authz.hasPermission('sys_route_view')")
    @GetMapping("/list")
    public ApiResult<List<SysRouteConf>> list() {
        return ApiResult.success(sysRouteConfService.findAllActiveRoutes());
    }

    @PreAuthorize("@authz.hasPermission('sys_route_view')")
    @GetMapping("/{id}")
    public ApiResult<SysRouteConf> getById(@PathVariable Long id) {
        return ApiResult.success(sysRouteConfService.getById(id));
    }

    @PreAuthorize("@authz.hasPermission('sys_route_add')")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysRouteConf route) {
        sysRouteConfService.save(route);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_route_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysRouteConf route) {
        sysRouteConfService.updateById(route);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_route_del')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        sysRouteConfService.removeById(id);
        return ApiResult.success();
    }
}