package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysMenu;
import com.smart.admin.entity.SysRoleMenu;
import com.smart.admin.entity.SysUser;
import com.smart.admin.entity.SysUserRole;
import com.smart.admin.mapper.SysMenuMapper;
import com.smart.admin.mapper.SysRoleMenuMapper;
import com.smart.admin.mapper.SysUserMapper;
import com.smart.admin.mapper.SysUserRoleMapper;
import com.smart.admin.service.SysMenuService;
import com.smart.common.core.enums.StatusFlag;
import com.smart.common.core.enums.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<SysMenu> findMenusByRoleId(Long roleId) {
        List<Long> menuIds = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId)
        ).stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());

        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return listByIds(menuIds);
    }

    @Override
    public List<SysMenu> findMenusByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Collections.emptyList();
        }

        List<SysMenu> filteredMenus;

        // Admin user (user_type = '0') gets all menus of type directory/menu
        if (UserType.ADMIN.getValue().equals(user.getUserType())) {
            filteredMenus = list(new LambdaQueryWrapper<SysMenu>()
                    .in(SysMenu::getMenuType, Arrays.asList("0", "1"))
                    .eq(SysMenu::getVisible, true)
                    .eq(SysMenu::getDelFlag, StatusFlag.NOT_DELETED.getValue())
                    .orderByAsc(SysMenu::getSortOrder));
        } else {
            // Normal user: get menus through role assignments
            List<Long> roleIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
            ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

            if (roleIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> menuIds = roleMenuMapper.selectList(
                    new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds)
            ).stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());

            if (menuIds.isEmpty()) {
                return Collections.emptyList();
            }

            filteredMenus = list(new LambdaQueryWrapper<SysMenu>()
                    .in(SysMenu::getMenuId, menuIds)
                    .in(SysMenu::getMenuType, Arrays.asList("0", "1"))
                    .eq(SysMenu::getVisible, true)
                    .eq(SysMenu::getDelFlag, StatusFlag.NOT_DELETED.getValue())
                    .orderByAsc(SysMenu::getSortOrder));
        }

        // Build tree structure from filtered menus
        Map<Long, List<SysMenu>> grouped = filteredMenus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        filteredMenus.forEach(menu -> menu.setChildren(grouped.get(menu.getMenuId())));

        return grouped.getOrDefault(0L, Collections.emptyList());
    }

    @Override
    public List<SysMenu> buildMenuTree() {
        List<SysMenu> allMenus = list(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDelFlag, StatusFlag.NOT_DELETED.getValue())
                .orderByAsc(SysMenu::getSortOrder));

        // P1-06: Build nested tree instead of returning flat list
        Map<Long, List<SysMenu>> grouped = allMenus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        allMenus.forEach(menu -> menu.setChildren(grouped.get(menu.getMenuId())));

        // Root menus have parentId = 0
        return grouped.getOrDefault(0L, Collections.emptyList());
    }
}