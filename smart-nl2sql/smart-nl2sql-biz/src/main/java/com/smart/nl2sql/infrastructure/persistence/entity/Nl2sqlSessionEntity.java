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
@TableName("nl2sql_session")
@TenantEntity
public class Nl2sqlSessionEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private Long datasetId;
    private Long modelId;
    private Long userId;
}