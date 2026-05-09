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
 * Department entity with ancestor path for tree queries.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
@TenantEntity
public class SysDept extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long deptId;

    private String deptName;

    private Long parentId;

    /**
     * Ancestor path, e.g. "0,1,2" for efficient subtree queries
     */
    private String ancestors;

    private Integer sortOrder;

    private String leader;

    private String phone;

    private String email;

    private Long tenantId;

    private String status;

    @TableField(exist = false)
    private List<SysDept> children;
}