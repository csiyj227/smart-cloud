package com.smart.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.admin.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * Find menu permissions by role IDs.
     */
    List<String> selectPermissionsByRoleIds(@Param("roleIds") List<Long> roleIds);
}