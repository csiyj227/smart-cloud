package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysRole;
import com.smart.admin.service.SysRoleService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.log.annotation.AuditTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @PreAuthorize("@authz.hasPermission('sys_role_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysRole>> page(Page<SysRole> page, SysRole query) {
        return ApiResult.success(sysRoleService.page(page, Wrappers.<SysRole>lambdaQuery()
                .like(query.getRoleName() != null && !query.getRoleName().isEmpty(), SysRole::getRoleName, query.getRoleName())
                .like(query.getRoleCode() != null && !query.getRoleCode().isEmpty(), SysRole::getRoleCode, query.getRoleCode())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysRole::getStatus, query.getStatus())));
    }

    @PreAuthorize("@authz.hasPermission('sys_role_view')")
    @GetMapping("/{roleId}")
    public ApiResult<SysRole> getById(@PathVariable Long roleId) {
        return ApiResult.success(sysRoleService.getById(roleId));
    }

    @PreAuthorize("@authz.hasPermission('sys_role_add')")
    @AuditTrace("新增角色")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysRole role) {
        sysRoleService.save(role);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_role_edit')")
    @AuditTrace("修改角色")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysRole role) {
        sysRoleService.updateById(role);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_role_del')")
    @AuditTrace("删除角色")
    @DeleteMapping("/{roleId}")
    public ApiResult<Void> delete(@PathVariable Long roleId) {
        sysRoleService.removeById(roleId);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_role_perm')")
    @AuditTrace("分配角色菜单权限")
    @PutMapping("/{roleId}/menus")
    public ApiResult<Void> saveRoleMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        sysRoleService.saveRoleMenus(roleId, menuIds);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_role_view')")
    @GetMapping("/{roleId}/menus")
    public ApiResult<List<Long>> getMenuIdsByRoleId(@PathVariable Long roleId) {
        return ApiResult.success(sysRoleService.getMenuIdsByRoleId(roleId));
    }

    @PreAuthorize("@authz.hasPermission('sys_role_perm')")
    @AuditTrace("分配角色数据权限")
    @PutMapping("/{roleId}/depts")
    public ApiResult<Void> saveRoleDepts(@PathVariable Long roleId, @RequestBody List<Long> deptIds) {
        sysRoleService.saveRoleDepts(roleId, deptIds);
        return ApiResult.success();
    }
}