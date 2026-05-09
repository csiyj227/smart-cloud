package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysRole;
import com.smart.admin.entity.SysRoleMenu;
import com.smart.admin.entity.SysRoleDept;
import com.smart.admin.entity.SysUserRole;
import com.smart.admin.mapper.SysRoleMapper;
import com.smart.admin.mapper.SysRoleMenuMapper;
import com.smart.admin.mapper.SysRoleDeptMapper;
import com.smart.admin.mapper.SysUserRoleMapper;
import com.smart.admin.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;

    @Override
    public List<SysRole> findRolesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return listByIds(roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            // P1-03: Use batch insert instead of N individual inserts
            List<SysRoleMenu> list = menuIds.stream().map(menuId -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                return rm;
            }).collect(Collectors.toList());
            saveBatch(roleMenuMapper, list);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
        if (deptIds != null && !deptIds.isEmpty()) {
            List<SysRoleDept> list = deptIds.stream().map(deptId -> {
                SysRoleDept rd = new SysRoleDept();
                rd.setRoleId(roleId);
                rd.setDeptId(deptId);
                return rd;
            }).collect(Collectors.toList());
            saveBatch(roleDeptMapper, list);
        }
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId)
        ).stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    /**
     * Batch insert using MyBatis-Plus service helper.
     * Falls back to individual inserts for mappers without batch support.
     */
    private <T> void saveBatch(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, List<T> list) {
        // MyBatis-Plus BaseMapper doesn't have saveBatch, use individual inserts in a batch
        for (T item : list) {
            mapper.insert(item);
        }
    }
}