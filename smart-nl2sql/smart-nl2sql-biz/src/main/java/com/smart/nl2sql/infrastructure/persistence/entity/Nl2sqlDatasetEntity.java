package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_dataset")
@TenantEntity
public class Nl2sqlDatasetEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Long datasourceId;
    private String description;
    private Integer learnStatus;
    private LocalDateTime learnTime;
    private Integer status;
}