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
@TableName("sys_post")
@TenantEntity
public class SysPost extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long postId;

    private String postCode;
    private String postName;
    private Integer sortOrder;
    private String status;
    private Long tenantId;
}