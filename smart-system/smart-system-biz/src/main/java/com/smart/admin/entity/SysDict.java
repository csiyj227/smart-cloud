package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict")
@TenantEntity
public class SysDict extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String typeCode;
    private String typeName;
    private String description;
    private Boolean systemFlag;
    private String status;
    private Long tenantId;
}