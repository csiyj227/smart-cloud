package com.smart.admin.controller;

import com.smart.admin.entity.SysMenu;
import com.smart.admin.service.SysMenuService;
import com.smart.admin.support.JwtClaimUtils;
import com.smart.common.core.web.ApiResult;
import com.smart.common.log.annotation.AuditTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/user-tree")
    public ApiResult<List<SysMenu>> userTree(Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        return ApiResult.success(sysMenuService.findMenusByUserId(userId));
    }

    @PreAuthorize("@authz.hasPermission('sys_menu_view')")
    @GetMapping("/tree")
    public ApiResult<List<SysMenu>> tree() {
        return ApiResult.success(sysMenuService.buildMenuTree());
    }

    @PreAuthorize("@authz.hasPermission('sys_menu_view')")
    @GetMapping("/{menuId}")
    public ApiResult<SysMenu> getById(@PathVariable Long menuId) {
        return ApiResult.success(sysMenuService.getById(menuId));
    }

    @PreAuthorize("@authz.hasPermission('sys_menu_add')")
    @AuditTrace("新增菜单")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysMenu menu) {
        sysMenuService.save(menu);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_menu_edit')")
    @AuditTrace("修改菜单")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysMenu menu) {
        sysMenuService.updateById(menu);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_menu_del')")
    @AuditTrace("删除菜单")
    @DeleteMapping("/{menuId}")
    public ApiResult<Void> delete(@PathVariable Long menuId) {
        sysMenuService.removeById(menuId);
        return ApiResult.success();
    }
}
