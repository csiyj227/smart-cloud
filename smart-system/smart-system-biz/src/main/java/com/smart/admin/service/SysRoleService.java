package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    List<SysRole> findRolesByUserId(Long userId);

    void saveRoleMenus(Long roleId, List<Long> menuIds);

    void saveRoleDepts(Long roleId, List<Long> deptIds);

    List<Long> getMenuIdsByRoleId(Long roleId);
}