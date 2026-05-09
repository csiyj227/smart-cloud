package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@TenantEntity
@TableName("sys_role_dept")
public class SysRoleDept implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;
    private Long deptId;
    private Long tenantId;
}