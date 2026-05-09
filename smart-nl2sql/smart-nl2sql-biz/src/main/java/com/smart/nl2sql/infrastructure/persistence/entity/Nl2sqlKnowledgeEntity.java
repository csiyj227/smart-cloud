package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_knowledge")
@TenantEntity
public class Nl2sqlKnowledgeEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;
    private String type;
    private String title;
    private String content;
    private Integer status;
}