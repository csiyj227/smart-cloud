package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysMenu;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {

    List<SysMenu> findMenusByRoleId(Long roleId);

    List<SysMenu> findMenusByUserId(Long userId);

    List<SysMenu> buildMenuTree();
}