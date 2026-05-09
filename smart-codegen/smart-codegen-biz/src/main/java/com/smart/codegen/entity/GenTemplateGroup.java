package com.smart.codegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_template_group")
public class GenTemplateGroup extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupName;
    private String groupCode;
    private String description;
    private Long tenantId;
}