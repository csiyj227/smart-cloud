package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * System role entity with data scope configuration.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
@TenantEntity
public class SysRole extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long roleId;

    private String roleName;

    private String roleCode;

    private String roleDesc;

    /**
     * Data scope type: 0=all, 1=custom, 2=dept, 3=dept_and_child, 4=self
     */
    private Integer dsType;

    /**
     * Custom dept IDs for ds_type=1 (comma-separated)
     */
    private String dsScope;

    private Long tenantId;

    private String status;
}