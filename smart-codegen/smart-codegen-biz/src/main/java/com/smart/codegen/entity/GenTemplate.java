package com.smart.codegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_template")
public class GenTemplate extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;
    private String templateName;
    private String templateCode;
    private String templateContent;
    private String filePath;
    private String fileExtension;
    private Integer sortOrder;
    private Long tenantId;
}