package com.smart.admin.service.impl;

import com.smart.admin.entity.SysDept;
import com.smart.admin.entity.SysRole;
import com.smart.admin.entity.SysRoleDept;
import com.smart.admin.entity.SysUser;
import com.smart.admin.entity.SysUserRole;
import com.smart.admin.mapper.SysDeptMapper;
import com.smart.admin.mapper.SysRoleDeptMapper;
import com.smart.admin.mapper.SysRoleMapper;
import com.smart.admin.mapper.SysUserMapper;
import com.smart.admin.mapper.SysUserRoleMapper;
import com.smart.common.data.datascope.DataPermission;
import com.smart.common.data.datascope.DataPermissionResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves data permission scope for the current authenticated user.
 * Reads the user's roles, finds the most restrictive data scope type,
 * and builds the DataPermission object accordingly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartDataPermissionResolver implements DataPermissionResolver {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    @Override
    public DataPermission resolveCurrentPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        Long userId = null;
        String username = null;

        if (principal instanceof OAuth2AuthenticatedPrincipal oauthPrincipal) {
            Object uid = oauthPrincipal.getAttribute("user_id");
            if (uid instanceof Number num) {
                userId = num.longValue();
            }
            username = oauthPrincipal.getName();
        }

        if (userId == null) {
            return null;
        }

        // Get user's roles
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).toList();

        if (roleIds.isEmpty()) {
            return new DataPermission()
                    .setFunc(DataPermission.PermissionFunc.SELF)
                    .setUsername(username);
        }

        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleId, roleIds)
        );

        if (roles.isEmpty()) {
            return new DataPermission()
                    .setFunc(DataPermission.PermissionFunc.SELF)
                    .setUsername(username);
        }

        // If any role has ALL access (dsType=0), grant ALL
        boolean hasAllAccess = roles.stream().anyMatch(r -> r.getDsType() == 0);
        if (hasAllAccess) {
            return null;
        }

        // Find the most restrictive dsType across all roles
        int minDsType = roles.stream().mapToInt(SysRole::getDsType).min().orElse(4);

        DataPermission.PermissionFunc func = switch (minDsType) {
            case 0 -> DataPermission.PermissionFunc.ALL;
            case 1 -> DataPermission.PermissionFunc.CUSTOM;
            case 2 -> DataPermission.PermissionFunc.DEPT;
            case 3 -> DataPermission.PermissionFunc.DEPT_AND_CHILD;
            case 4 -> DataPermission.PermissionFunc.SELF;
            default -> DataPermission.PermissionFunc.ALL;
        };

        if (func == DataPermission.PermissionFunc.ALL) {
            return null;
        }

        DataPermission permission = new DataPermission()
                .setFunc(func)
                .setUsername(username);

        // Resolve dept IDs based on scope type
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        List<Long> deptIds = new ArrayList<>();
        for (SysRole role : roles) {
            switch (role.getDsType()) {
                case 1 -> { // CUSTOM
                    List<Long> customDeptIds = roleDeptMapper.selectList(
                            new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, role.getRoleId())
                    ).stream().map(SysRoleDept::getDeptId).toList();
                    deptIds.addAll(customDeptIds);
                }
                case 2 -> { // DEPT
                    if (user.getDeptId() != null && user.getDeptId() > 0) {
                        deptIds.add(user.getDeptId());
                    }
                }
                case 3 -> { // DEPT_AND_CHILD
                    if (user.getDeptId() != null && user.getDeptId() > 0) {
                        SysDept dept = deptMapper.selectById(user.getDeptId());
                        if (dept != null) {
                            deptIds.add(dept.getDeptId());
                            List<SysDept> children = deptMapper.selectList(
                                    new LambdaQueryWrapper<SysDept>()
                                            .like(SysDept::getAncestors, dept.getDeptId())
                            );
                            children.forEach(d -> deptIds.add(d.getDeptId()));
                        }
                    }
                }
            }
        }

        permission.setDeptIds(deptIds.stream().distinct().toList());
        return permission;
    }

    @Override
    public List<Long> resolveDeptIds(Long roleId) {
        return roleDeptMapper.selectList(
                new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId)
        ).stream().map(SysRoleDept::getDeptId).toList();
    }

    @Override
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }
}