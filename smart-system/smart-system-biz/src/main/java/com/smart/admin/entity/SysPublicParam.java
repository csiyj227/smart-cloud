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
@TableName("sys_public_param")
@TenantEntity
public class SysPublicParam extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paramName;
    private String paramKey;
    private String paramValue;
    private String paramType;
    private String status;
    private Long tenantId;
}