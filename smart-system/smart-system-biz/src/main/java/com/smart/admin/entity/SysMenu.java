package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * System menu/permission entity.
 * Types: 0=directory, 1=menu, 2=button
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = "children")
@TableName("sys_menu")
@TenantEntity
public class SysMenu extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long menuId;

    private Long tenantId;

    private String menuName;

    private String permission;

    private String path;

    private String component;

    private Long parentId;

    private String icon;

    private Integer sortOrder;

    /**
     * Menu type: 0=directory, 1=menu, 2=button
     */
    private String menuType;

    private Boolean keepAlive;

    private Boolean visible;

    @TableField(exist = false)
    private List<SysMenu> children;
}